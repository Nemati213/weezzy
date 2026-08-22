package ru.itmo.nemat.weezzy.lunch.group;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.location.Location;
import ru.itmo.nemat.weezzy.location.LocationRepository;
import ru.itmo.nemat.weezzy.location.LocationType;
import ru.itmo.nemat.weezzy.location.University;
import ru.itmo.nemat.weezzy.location.UniversityRepository;
import ru.itmo.nemat.weezzy.lunch.group.lifecycle.LunchGroupLifecycleService;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestRepository;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestStatus;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false",
		"app.lunch.lifecycle.enabled=false",
		"app.outbox.worker.enabled=false",
		"app.outbox.cleanup.enabled=false"
})
class LunchGroupApiAndLifecycleTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");
	private static final LocalDateTime NOW = LocalDateTime.of(
			2026,
			8,
			22,
			14,
			0
	);

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private LunchGroupLifecycleService lifecycleService;

	@Autowired
	private LunchGroupRepository groupRepository;

	@Autowired
	private LunchGroupMemberRepository memberRepository;

	@Autowired
	private LunchRequestRepository requestRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private UniversityRepository universityRepository;

	@Autowired
	private LocationRepository locationRepository;

	private Location location;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void setUp() {
		location = createLocation();
	}

	@Test
	void participantCanReadCurrentGroupWithoutPrivateContacts() throws Exception {
		ApiParticipant first = createApiParticipant("First participant");
		ApiParticipant second = createApiParticipant("Second participant");
		LunchGroup group = createGroup(NOW.plusMinutes(30));
		createMember(group, first.profile());
		createMember(group, second.profile());

		mockMvc.perform(first.user().authorize(get("/api/lunch/groups/me")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(group.getId().toString()))
				.andExpect(jsonPath("$.location.id")
						.value(location.getId().toString()))
				.andExpect(jsonPath("$.timeSlot").value("2026-08-22T14:30:00"))
				.andExpect(jsonPath("$.topic").value("STUDY"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.members.length()").value(2))
				.andExpect(jsonPath("$.members[*].displayName", containsInAnyOrder(
						first.profile().getDisplayName(),
						second.profile().getDisplayName()
				)))
				.andExpect(jsonPath("$.members[0].telegram").doesNotExist())
				.andExpect(jsonPath("$.members[1].telegram").doesNotExist());
	}

	@Test
	void outsiderAndAnonymousUserCannotReadGroup() throws Exception {
		ApiParticipant participant = createApiParticipant("Participant");
		ApiParticipant outsider = createApiParticipant("Outsider");
		LunchGroup group = createGroup(NOW.plusMinutes(30));
		createMember(group, participant.profile());

		mockMvc.perform(outsider.user().authorize(get("/api/lunch/groups/me")))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/lunch/groups/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void completedGroupIsNoLongerCurrent() throws Exception {
		ApiParticipant participant = createApiParticipant("Participant");
		LunchGroup group = createGroup(NOW.minusHours(1));
		createMember(group, participant.profile());

		assertThat(lifecycleService.completeDueGroups(NOW, 100))
				.containsExactly(group.getId());
		mockMvc.perform(participant.user().authorize(get("/api/lunch/groups/me")))
				.andExpect(status().isNotFound());

		LunchGroup completed = groupRepository.findById(group.getId()).orElseThrow();
		assertThat(completed.getStatus()).isEqualTo(LunchGroupStatus.COMPLETED);
		assertThat(completed.getCompletedAt()).isEqualTo(NOW);
		assertThat(completed.getCancelledAt()).isNull();
		assertThat(lifecycleService.completeDueGroups(NOW, 100)).isEmpty();
	}

	@Test
	void groupDoesNotCompleteBeforeDurationElapses() {
		LunchGroup group = createGroup(NOW.minusHours(1).plusNanos(1));

		assertThat(lifecycleService.completeDueGroups(NOW, 100)).isEmpty();
		assertThat(groupRepository.findById(group.getId()).orElseThrow().getStatus())
				.isEqualTo(LunchGroupStatus.ACTIVE);
	}

	@Test
	void parallelCompletionBatchesCompleteEachGroupOnce() throws Exception {
		List<UUID> groupIds = new ArrayList<>();
		for (int index = 0; index < 20; index++) {
			groupIds.add(createGroup(NOW.minusHours(2)).getId());
		}
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<List<UUID>> first = executor.submit(() -> completeAfterSignal(
					ready,
					start
			));
			Future<List<UUID>> second = executor.submit(() -> completeAfterSignal(
					ready,
					start
			));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<UUID> completed = new ArrayList<>(first.get(10, TimeUnit.SECONDS));
			completed.addAll(second.get(10, TimeUnit.SECONDS));
			assertThat(completed).containsExactlyInAnyOrderElementsOf(groupIds);
			assertThat(new HashSet<>(completed)).hasSize(groupIds.size());
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(groupRepository.findAllById(groupIds))
				.extracting(LunchGroup::getStatus)
				.containsOnly(LunchGroupStatus.COMPLETED);
	}

	private List<UUID> completeAfterSignal(
			CountDownLatch ready,
			CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Group lifecycle start signal timed out");
		}
		return lifecycleService.completeDueGroups(NOW, 100);
	}

	private ApiParticipant createApiParticipant(String displayName) throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		AuthenticatedTestUser.TestProfile created = user.createProfile(
				displayName + " " + UUID.randomUUID()
		);
		Profile profile = profileRepository.findById(
				UUID.fromString(created.id())
		).orElseThrow();
		profile.setStatus(ProfileStatus.ACTIVE);
		profile = profileRepository.saveAndFlush(profile);
		return new ApiParticipant(user, profile);
	}

	private LunchGroup createGroup(LocalDateTime timeSlot) {
		LunchGroup group = new LunchGroup();
		group.setLocation(location);
		group.setTimeSlot(timeSlot);
		group.setTopic(LunchTopic.STUDY);
		return groupRepository.saveAndFlush(group);
	}

	private void createMember(LunchGroup group, Profile profile) {
		LunchRequest request = new LunchRequest();
		request.setProfile(profile);
		request.setLocation(location);
		request.setStatus(LunchRequestStatus.MATCHED);
		request.setTopic(group.getTopic());
		request.setTimeSlot(group.getTimeSlot());
		request = requestRepository.saveAndFlush(request);

		LunchGroupMember member = new LunchGroupMember();
		member.setId(new LunchGroupMemberId(group.getId(), profile.getId()));
		member.setGroup(group);
		member.setProfile(profile);
		member.setLunchRequest(request);
		memberRepository.saveAndFlush(member);
	}

	private Location createLocation() {
		University university = new University();
		university.setName("Group API University " + UUID.randomUUID());
		university.setCity("Saint Petersburg");
		university = universityRepository.saveAndFlush(university);

		Location newLocation = new Location();
		newLocation.setUniversity(university);
		newLocation.setType(LocationType.DINING_ROOM);
		newLocation.setName("Group API Canteen " + UUID.randomUUID());
		newLocation.setAddress("Kronverksky Prospekt 49");
		return locationRepository.saveAndFlush(newLocation);
	}

	private record ApiParticipant(AuthenticatedTestUser user, Profile profile) {
	}
}
