package ru.itmo.nemat.weezzy.lunch.group;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlock;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockRepository;
import ru.itmo.nemat.weezzy.location.Location;
import ru.itmo.nemat.weezzy.location.LocationRepository;
import ru.itmo.nemat.weezzy.location.LocationType;
import ru.itmo.nemat.weezzy.location.University;
import ru.itmo.nemat.weezzy.location.UniversityRepository;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestRepository;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestStatus;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanction;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionRepository;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;
import ru.itmo.nemat.weezzy.notification.Notification;
import ru.itmo.nemat.weezzy.notification.NotificationRepository;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventRepository;
import ru.itmo.nemat.weezzy.outbox.OutboxEventStatus;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.handler.LunchGroupFormedEventHandler;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = "app.lunch.matching.enabled=false")
class LunchGroupFormationServiceTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private LunchGroupFormationService formationService;

	@Autowired
	private LunchGroupRepository groupRepository;

	@Autowired
	private LunchGroupMemberRepository memberRepository;

	@Autowired
	private LunchRequestRepository requestRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private UniversityRepository universityRepository;

	@Autowired
	private LocationRepository locationRepository;

	@Autowired
	private ProfileBlockRepository blockRepository;

	@Autowired
	private AccountSanctionRepository sanctionRepository;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private LunchGroupFormedEventHandler formedEventHandler;

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
	void formsGroupAndMarksEveryRequestAsMatched() {
		List<Participant> participants = List.of(
				createParticipant("First", location, slot()),
				createParticipant("Second", location, slot()),
				createParticipant("Third", location, slot())
		);

		LunchGroup group = formationService.formGroup(
				List.of(
						participants.get(2).request().getId(),
						participants.get(0).request().getId(),
						participants.get(1).request().getId()
				),
				LunchTopic.STUDY
		);

		assertThat(group.getStatus()).isEqualTo(LunchGroupStatus.ACTIVE);
		assertThat(group.getLocation().getId()).isEqualTo(location.getId());
		assertThat(group.getTimeSlot()).isEqualTo(slot());
		assertThat(group.getTopic()).isEqualTo(LunchTopic.STUDY);
		assertThat(memberRepository.countByGroupId(group.getId())).isEqualTo(3);
		assertThat(requestRepository.findAllById(requestIds(participants)))
				.extracting(LunchRequest::getStatus)
				.containsOnly(LunchRequestStatus.MATCHED);
	}

	@Test
	void repeatedFormationOfTheSameCandidatesIsIdempotent() {
		List<Participant> participants = List.of(
				createParticipant("First", location, slot()),
				createParticipant("Second", location, slot())
		);
		List<UUID> requestIds = requestIds(participants);
		long groupsBefore = groupRepository.count();

		LunchGroup first = formationService.formGroup(requestIds, LunchTopic.NETWORKING);
		LunchGroup repeated = formationService.formGroup(
				requestIds.reversed(),
				LunchTopic.NETWORKING
		);

		assertThat(repeated.getId()).isEqualTo(first.getId());
		assertThat(groupRepository.count()).isEqualTo(groupsBefore + 1);
		assertThat(memberRepository.countByGroupId(first.getId())).isEqualTo(2);
		OutboxEvent event = formationEvent(first.getId());
		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(event.getPayload()).containsOnlyKeys("groupId");
	}

	@Test
	void activeSanctionPreventsFormationWithoutPartialChanges() {
		Participant first = createParticipant("First", location, slot());
		Participant second = createParticipant("Second", location, slot());
		AccountSanction sanction = new AccountSanction();
		sanction.setTargetUserId(second.profile().getUser().getId());
		sanction.setTargetProfileId(second.profile().getId());
		sanction.setType(AccountSanctionType.PERMANENT_BAN);
		sanction.setReason("Formation eligibility test");
		sanction.setCreatedByUserId(first.profile().getUser().getId());
		sanctionRepository.saveAndFlush(sanction);
		long groupsBefore = groupRepository.count();
		long formationEventsBefore = formationEventCount();

		assertThatThrownBy(() -> formationService.formGroup(
				List.of(first.request().getId(), second.request().getId()),
				LunchTopic.STUDY
		)).isInstanceOf(LunchGroupFormationConflictException.class)
				.hasMessageContaining("active sanction");

		assertThat(groupRepository.count()).isEqualTo(groupsBefore);
		assertThat(requestRepository.findAllById(List.of(
				first.request().getId(),
				second.request().getId()
		))).extracting(LunchRequest::getStatus)
				.containsOnly(LunchRequestStatus.SEARCHING);
		assertThat(formationEventCount()).isEqualTo(formationEventsBefore);
	}

	@Test
	void activeGroupPreventsAProfileFromJoiningAnotherGroup() {
		Participant first = createParticipant("First", location, slot());
		Participant second = createParticipant("Second", location, slot());
		LunchRequest previousRequest = new LunchRequest();
		previousRequest.setProfile(first.profile());
		previousRequest.setLocation(location);
		previousRequest.setStatus(LunchRequestStatus.MATCHED);
		previousRequest.setTopic(LunchTopic.STUDY);
		previousRequest.setTimeSlot(slot().minusDays(1));
		previousRequest = requestRepository.saveAndFlush(previousRequest);
		LunchGroup activeGroup = new LunchGroup();
		activeGroup.setLocation(location);
		activeGroup.setTimeSlot(previousRequest.getTimeSlot());
		activeGroup.setTopic(LunchTopic.STUDY);
		activeGroup = groupRepository.saveAndFlush(activeGroup);
		LunchGroupMember member = new LunchGroupMember();
		member.setId(new LunchGroupMemberId(
				activeGroup.getId(),
				first.profile().getId()
		));
		member.setGroup(activeGroup);
		member.setProfile(first.profile());
		member.setLunchRequest(previousRequest);
		memberRepository.saveAndFlush(member);

		assertThatThrownBy(() -> formationService.formGroup(
				List.of(first.request().getId(), second.request().getId()),
				LunchTopic.STUDY
		)).isInstanceOf(LunchGroupFormationConflictException.class)
				.hasMessageContaining("active group");

		assertThat(requestRepository.findAllById(List.of(
				first.request().getId(),
				second.request().getId()
		))).extracting(LunchRequest::getStatus)
				.containsOnly(LunchRequestStatus.SEARCHING);
	}

	@Test
	void formedEventNotifiesEveryParticipantIdempotently() {
		List<Participant> participants = List.of(
				createParticipant("First", location, slot()),
				createParticipant("Second", location, slot()),
				createParticipant("Third", location, slot())
		);
		LunchGroup group = formationService.formGroup(
				requestIds(participants),
				LunchTopic.NETWORKING
		);
		OutboxEvent event = formationEvent(group.getId());

		formedEventHandler.handle(event);
		formedEventHandler.handle(event);

		List<Notification> notifications = notificationRepository.findAll().stream()
				.filter(notification -> notification.getSourceEventId().equals(event.getId()))
				.toList();
		assertThat(notifications).hasSize(3);
		assertThat(notifications)
				.extracting(Notification::getRecipientUserId)
				.containsExactlyInAnyOrderElementsOf(participants.stream()
						.map(participant -> participant.profile().getUser().getId())
						.toList());
		assertThat(notifications)
				.extracting(Notification::getType)
				.containsOnly(NotificationType.LUNCH_GROUP_FORMED);
		assertThat(notifications).allSatisfy(notification -> {
			assertThat(notification.getPayload()).containsOnlyKeys(
					"groupId",
					"locationId",
					"timeSlot",
					"topic"
			);
			assertThat(String.valueOf(notification.getPayload().get("groupId")))
					.isEqualTo(group.getId().toString());
			assertThat(String.valueOf(notification.getPayload().get("locationId")))
					.isEqualTo(location.getId().toString());
			assertThat(String.valueOf(notification.getPayload().get("topic")))
					.isEqualTo(LunchTopic.NETWORKING.name());
		});
	}

	@Test
	void overlappingFormationCannotReuseAMatchedRequest() {
		Participant first = createParticipant("First", location, slot());
		Participant second = createParticipant("Second", location, slot());
		Participant third = createParticipant("Third", location, slot());
		formationService.formGroup(
				List.of(first.request().getId(), second.request().getId()),
				LunchTopic.STUDY
		);
		long groupsBefore = groupRepository.count();

		assertThatThrownBy(() -> formationService.formGroup(
				List.of(first.request().getId(), third.request().getId()),
				LunchTopic.STUDY
		)).isInstanceOf(LunchGroupFormationConflictException.class);

		assertThat(groupRepository.count()).isEqualTo(groupsBefore);
		assertThat(requestRepository.findById(third.request().getId()).orElseThrow()
				.getStatus()).isEqualTo(LunchRequestStatus.SEARCHING);
	}

	@Test
	void requestsFromDifferentBucketsAreRejectedWithoutPartialChanges() {
		Participant first = createParticipant("First", location, slot());
		Participant second = createParticipant(
				"Second",
				createLocation(),
				slot()
		);
		long groupsBefore = groupRepository.count();

		assertThatThrownBy(() -> formationService.formGroup(
				List.of(first.request().getId(), second.request().getId()),
				LunchTopic.CASUAL_CHAT
		)).isInstanceOf(LunchGroupFormationConflictException.class);

		assertThat(groupRepository.count()).isEqualTo(groupsBefore);
		assertThat(requestRepository.findAllById(List.of(
				first.request().getId(),
				second.request().getId()
		))).extracting(LunchRequest::getStatus)
				.containsOnly(LunchRequestStatus.SEARCHING);
	}

	@Test
	void nonSearchingRequestIsRejectedWithoutPartialChanges() {
		Participant first = createParticipant("First", location, slot());
		Participant second = createParticipant("Second", location, slot());
		second.request().setStatus(LunchRequestStatus.CANCELLED);
		requestRepository.saveAndFlush(second.request());

		assertThatThrownBy(() -> formationService.formGroup(
				List.of(first.request().getId(), second.request().getId()),
				LunchTopic.STUDY
		)).isInstanceOf(LunchGroupFormationConflictException.class);

		assertThat(requestRepository.findById(first.request().getId()).orElseThrow()
				.getStatus()).isEqualTo(LunchRequestStatus.SEARCHING);
	}

	@Test
	void inactiveProfileIsRejected() {
		Participant first = createParticipant("First", location, slot());
		Participant second = createParticipant("Second", location, slot());
		second.profile().setStatus(ProfileStatus.HIDDEN);
		profileRepository.saveAndFlush(second.profile());

		assertThatThrownBy(() -> formationService.formGroup(
				List.of(first.request().getId(), second.request().getId()),
				LunchTopic.STUDY
		)).isInstanceOf(LunchGroupFormationConflictException.class)
				.hasMessageContaining("profile is not eligible");
	}

	@Test
	void blockInEitherDirectionPreventsFormation() {
		Participant first = createParticipant("First", location, slot());
		Participant second = createParticipant("Second", location, slot());
		ProfileBlock block = new ProfileBlock();
		block.setBlockerProfileId(second.profile().getId());
		block.setBlockedProfileId(first.profile().getId());
		blockRepository.saveAndFlush(block);

		assertThatThrownBy(() -> formationService.formGroup(
				List.of(first.request().getId(), second.request().getId()),
				LunchTopic.STUDY
		)).isInstanceOf(LunchGroupFormationConflictException.class)
				.hasMessageContaining("profiles are blocked");
	}

	@Test
	void duplicateCandidateIdsAreRejected() {
		Participant participant = createParticipant("First", location, slot());

		assertThatThrownBy(() -> formationService.formGroup(
				List.of(participant.request().getId(), participant.request().getId()),
				LunchTopic.STUDY
		)).isInstanceOf(InvalidLunchGroupCandidatesException.class)
				.hasMessageContaining("must be unique");
	}

	@Test
	void concurrentRepeatedFormationReturnsOneGroupWithoutDeadlock() throws Exception {
		List<Participant> participants = List.of(
				createParticipant("First", location, slot()),
				createParticipant("Second", location, slot()),
				createParticipant("Third", location, slot())
		);
		List<UUID> requestIds = requestIds(participants);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		long groupsBefore = groupRepository.count();

		try {
			Future<UUID> first = executor.submit(() -> formAfterSignal(
					requestIds,
					ready,
					start
			));
			Future<UUID> second = executor.submit(() -> formAfterSignal(
					requestIds.reversed(),
					ready,
					start
			));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(first.get(10, TimeUnit.SECONDS))
					.isEqualTo(second.get(10, TimeUnit.SECONDS));
			assertThat(groupRepository.count()).isEqualTo(groupsBefore + 1);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void concurrentOverlappingFormationsProduceOneWinnerWithoutDeadlock()
			throws Exception {
		List<Participant> participants = List.of(
				createParticipant("First", location, slot()),
				createParticipant("Second", location, slot()),
				createParticipant("Third", location, slot()),
				createParticipant("Fourth", location, slot())
		);
		List<UUID> firstCandidates = requestIds(participants.subList(0, 3));
		List<UUID> secondCandidates = List.of(
				participants.get(3).request().getId(),
				participants.get(2).request().getId(),
				participants.get(1).request().getId()
		);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		long groupsBefore = groupRepository.count();

		try {
			List<Future<UUID>> results = List.of(
					executor.submit(() -> formAfterSignal(
							firstCandidates,
							ready,
							start
					)),
					executor.submit(() -> formAfterSignal(
							secondCandidates,
							ready,
							start
					))
			);
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			int successCount = 0;
			int conflictCount = 0;
			for (Future<UUID> result : results) {
				try {
					result.get(10, TimeUnit.SECONDS);
					successCount++;
				} catch (ExecutionException exception) {
					assertThat(exception.getCause())
							.isInstanceOf(LunchGroupFormationConflictException.class);
					conflictCount++;
				}
			}

			assertThat(successCount).isEqualTo(1);
			assertThat(conflictCount).isEqualTo(1);
			assertThat(groupRepository.count()).isEqualTo(groupsBefore + 1);
			assertThat(requestRepository.findAllById(requestIds(participants)))
					.extracting(LunchRequest::getStatus)
					.containsExactlyInAnyOrder(
							LunchRequestStatus.MATCHED,
							LunchRequestStatus.MATCHED,
							LunchRequestStatus.MATCHED,
							LunchRequestStatus.SEARCHING
					);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	private UUID formAfterSignal(
			List<UUID> requestIds,
			CountDownLatch ready,
			CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Formation start signal timed out");
		}
		return formationService.formGroup(requestIds, LunchTopic.STARTUPS).getId();
	}

	private List<UUID> requestIds(List<Participant> participants) {
		return participants.stream()
				.map(participant -> participant.request().getId())
				.toList();
	}

	private OutboxEvent formationEvent(UUID groupId) {
		List<OutboxEvent> events = outboxEventRepository.findAll().stream()
				.filter(event -> event.getEventType()
						== OutboxEventType.LUNCH_GROUP_FORMED)
				.filter(event -> eventPayloadId(event).equals(groupId.toString()))
				.toList();
		assertThat(events).hasSize(1);
		return events.getFirst();
	}

	private String eventPayloadId(OutboxEvent event) {
		return String.valueOf(event.getPayload().get("groupId"));
	}

	private long formationEventCount() {
		return outboxEventRepository.findAll().stream()
				.filter(event -> event.getEventType()
						== OutboxEventType.LUNCH_GROUP_FORMED)
				.count();
	}

	private Participant createParticipant(
			String displayName,
			Location participantLocation,
			LocalDateTime timeSlot
	) {
		User user = new User();
		user.setEmail("lunch-" + UUID.randomUUID() + "@example.com");
		user.setPasswordHash("test-password-hash");
		user.setEmailVerifiedAt(LocalDateTime.now());
		user = userRepository.saveAndFlush(user);

		Profile profile = new Profile();
		profile.setDisplayName(displayName + " " + UUID.randomUUID());
		profile.setStatus(ProfileStatus.ACTIVE);
		profile.setUser(user);
		profile = profileRepository.saveAndFlush(profile);

		LunchRequest request = new LunchRequest();
		request.setProfile(profile);
		request.setLocation(participantLocation);
		request.setStatus(LunchRequestStatus.SEARCHING);
		request.setTopic(LunchTopic.STUDY);
		request.setTimeSlot(timeSlot);
		request = requestRepository.saveAndFlush(request);
		return new Participant(profile, request);
	}

	private Location createLocation() {
		University university = new University();
		university.setName("Formation University " + UUID.randomUUID());
		university.setCity("Saint Petersburg");
		university = universityRepository.saveAndFlush(university);

		Location newLocation = new Location();
		newLocation.setUniversity(university);
		newLocation.setType(LocationType.DINING_ROOM);
		newLocation.setName("Formation Canteen " + UUID.randomUUID());
		newLocation.setAddress("Kronverksky Prospekt 49");
		return locationRepository.saveAndFlush(newLocation);
	}

	private LocalDateTime slot() {
		return LocalDateTime.of(2026, 8, 22, 12, 30);
	}

	private record Participant(Profile profile, LunchRequest request) {
	}
}
