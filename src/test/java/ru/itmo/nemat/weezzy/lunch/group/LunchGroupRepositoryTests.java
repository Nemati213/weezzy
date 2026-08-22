package ru.itmo.nemat.weezzy.lunch.group;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.location.Location;
import ru.itmo.nemat.weezzy.location.LocationRepository;
import ru.itmo.nemat.weezzy.location.LocationType;
import ru.itmo.nemat.weezzy.location.University;
import ru.itmo.nemat.weezzy.location.UniversityRepository;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestRepository;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestStatus;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class LunchGroupRepositoryTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private UniversityRepository universityRepository;

	@Autowired
	private LocationRepository locationRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private LunchRequestRepository lunchRequestRepository;

	@Autowired
	private LunchGroupRepository groupRepository;

	@Autowired
	private LunchGroupMemberRepository memberRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
	void groupCanBeSavedAndFoundByStatus() {
		LunchGroup group = createGroup(LunchGroupStatus.ACTIVE);

		LunchGroup found = groupRepository
				.findByIdAndStatus(group.getId(), LunchGroupStatus.ACTIVE)
				.orElseThrow();

		assertThat(found.getLocation().getId()).isEqualTo(location.getId());
		assertThat(found.getTopic()).isEqualTo(LunchTopic.STUDY);
		assertThat(found.getTimeSlot()).isEqualTo(slot());
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
	}

	@Test
	void membersCanBeReadAndCountedByGroup() {
		LunchGroup group = createGroup(LunchGroupStatus.ACTIVE);
		LunchGroupMember first = createMember(group, createProfileAndRequest("First"));
		LunchGroupMember second = createMember(group, createProfileAndRequest("Second"));

		List<LunchGroupMember> members =
				memberRepository.findByGroupIdOrderByJoinedAtAsc(group.getId());

		assertThat(members).extracting(member -> member.getProfile().getId())
				.containsExactlyInAnyOrder(
						first.getProfile().getId(),
						second.getProfile().getId()
				);
		assertThat(memberRepository.countByGroupId(group.getId())).isEqualTo(2);
		assertThat(memberRepository.existsByLunchRequestId(
				first.getLunchRequest().getId()
		)).isTrue();
	}

	@Test
	void lunchRequestCannotBeReusedInAnotherGroup() {
		ProfileAndRequest participant = createProfileAndRequest("Participant");
		LunchGroup firstGroup = createGroup(LunchGroupStatus.ACTIVE);
		createMember(firstGroup, participant);
		LunchGroup secondGroup = createGroup(LunchGroupStatus.ACTIVE);

		LunchGroupMember duplicate = member(secondGroup, participant);

		assertThatThrownBy(() -> memberRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void profileCannotBeAddedTwiceToSameGroup() {
		LunchGroup group = createGroup(LunchGroupStatus.ACTIVE);
		ProfileAndRequest firstRequest = createProfileAndRequest("Participant");
		createMember(group, firstRequest);
		LunchRequest secondRequest = createRequest(firstRequest.profile());

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
						INSERT INTO lunch_group_members (
						    group_id, profile_id, lunch_request_id, joined_at
						) VALUES (?, ?, ?, NOW())
						""",
				group.getId(),
				firstRequest.profile().getId(),
				secondRequest.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void activeGroupLookupIgnoresCompletedGroup() {
		LunchGroup group = createGroup(LunchGroupStatus.ACTIVE);
		ProfileAndRequest participant = createProfileAndRequest("Participant");
		createMember(group, participant);

		assertThat(memberRepository.findGroupByProfileIdAndStatus(
				participant.profile().getId(),
				LunchGroupStatus.ACTIVE
		).map(LunchGroup::getId)).contains(group.getId());

		group.setStatus(LunchGroupStatus.COMPLETED);
		group.setCompletedAt(LocalDateTime.now());
		groupRepository.saveAndFlush(group);

		assertThat(memberRepository.findGroupByProfileIdAndStatus(
				participant.profile().getId(),
				LunchGroupStatus.ACTIVE
		)).isEmpty();
	}

	private LunchGroup createGroup(LunchGroupStatus status) {
		LunchGroup group = new LunchGroup();
		group.setLocation(location);
		group.setTimeSlot(slot());
		group.setTopic(LunchTopic.STUDY);
		group.setStatus(status);
		return groupRepository.saveAndFlush(group);
	}

	private LunchGroupMember createMember(
			LunchGroup group,
			ProfileAndRequest participant
	) {
		return memberRepository.saveAndFlush(member(group, participant));
	}

	private LunchGroupMember member(
			LunchGroup group,
			ProfileAndRequest participant
	) {
		LunchGroupMember member = new LunchGroupMember();
		member.setId(new LunchGroupMemberId(
				group.getId(),
				participant.profile().getId()
		));
		member.setGroup(group);
		member.setProfile(participant.profile());
		member.setLunchRequest(participant.request());
		return member;
	}

	private ProfileAndRequest createProfileAndRequest(String displayName) {
		Profile profile = new Profile();
		profile.setDisplayName(displayName + " " + UUID.randomUUID());
		profile.setStatus(ProfileStatus.ACTIVE);
		profile = profileRepository.saveAndFlush(profile);
		return new ProfileAndRequest(profile, createRequest(profile));
	}

	private LunchRequest createRequest(Profile profile) {
		LunchRequest request = new LunchRequest();
		request.setProfile(profile);
		request.setLocation(location);
		request.setStatus(LunchRequestStatus.MATCHED);
		request.setTopic(LunchTopic.STUDY);
		request.setTimeSlot(slot());
		return lunchRequestRepository.saveAndFlush(request);
	}

	private Location createLocation() {
		University university = new University();
		university.setName("Group Test University " + UUID.randomUUID());
		university.setCity("Saint Petersburg");
		university = universityRepository.saveAndFlush(university);

		Location lunchLocation = new Location();
		lunchLocation.setUniversity(university);
		lunchLocation.setType(LocationType.DINING_ROOM);
		lunchLocation.setName("Group Test Canteen " + UUID.randomUUID());
		lunchLocation.setAddress("Kronverksky Prospekt 49");
		return locationRepository.saveAndFlush(lunchLocation);
	}

	private LocalDateTime slot() {
		return LocalDateTime.of(2026, 8, 16, 12, 30);
	}

	private record ProfileAndRequest(Profile profile, LunchRequest request) {
	}
}
