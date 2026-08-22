package ru.itmo.nemat.weezzy.lunch.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMember;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMemberRepository;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupRepository;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestRepository;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestStatus;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanction;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionRepository;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;
import ru.itmo.nemat.weezzy.outbox.OutboxEventRepository;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false",
		"app.outbox.worker.enabled=false",
		"app.outbox.cleanup.enabled=false"
})
class LunchMatchingWorkerTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private LunchMatchingBucketProcessor bucketProcessor;

	@Autowired
	private LunchMatchingRepository matchingRepository;

	@Autowired
	private LunchRequestRepository requestRepository;

	@Autowired
	private LunchGroupRepository groupRepository;

	@Autowired
	private LunchGroupMemberRepository memberRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private ProfileBlockRepository blockRepository;

	@Autowired
	private AccountSanctionRepository sanctionRepository;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private UniversityRepository universityRepository;

	@Autowired
	private LocationRepository locationRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

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
	void concurrentWorkersFormEveryGroupOnceAndRetryIsIdempotent()
			throws Exception {
		List<Participant> participants = List.of(
				createParticipant(LunchTopic.STUDY),
				createParticipant(LunchTopic.STUDY),
				createParticipant(LunchTopic.STUDY),
				createParticipant(LunchTopic.STUDY),
				createParticipant(LunchTopic.STARTUPS),
				createParticipant(LunchTopic.NETWORKING)
		);
		MatchingBucketKey bucketKey = bucketKey();
		LocalDateTime now = slot().minusMinutes(1);
		long groupsBefore = groupRepository.count();
		long eventsBefore = formationEventCount();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<LunchMatchingBucketProcessingResult> first = executor.submit(() ->
					processAfterSignal(bucketKey, now, ready, start));
			Future<LunchMatchingBucketProcessingResult> second = executor.submit(() ->
					processAfterSignal(bucketKey, now, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<LunchMatchingBucketProcessingResult> results = List.of(
					first.get(10, TimeUnit.SECONDS),
					second.get(10, TimeUnit.SECONDS)
			);
			assertThat(results.stream()
					.mapToInt(LunchMatchingBucketProcessingResult::formedGroupCount)
					.sum()).isEqualTo(2);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(groupRepository.count()).isEqualTo(groupsBefore + 2);
		assertThat(memberRepository.findAllByLunchRequestIds(requestIds(participants)))
				.hasSize(6)
				.extracting(LunchGroupMember::getLunchRequest)
				.extracting(LunchRequest::getId)
				.containsExactlyInAnyOrderElementsOf(requestIds(participants));
		assertThat(requestRepository.findAllById(requestIds(participants)))
				.extracting(LunchRequest::getStatus)
				.containsOnly(LunchRequestStatus.MATCHED);
		assertThat(formationEventCount()).isEqualTo(eventsBefore + 2);

		LunchMatchingBucketProcessingResult retry = bucketProcessor.process(
				bucketKey,
				now
		);
		assertThat(retry.claimed()).isTrue();
		assertThat(retry.formedGroupCount()).isZero();
		assertThat(groupRepository.count()).isEqualTo(groupsBefore + 2);
		assertThat(formationEventCount()).isEqualTo(eventsBefore + 2);
	}

	@Test
	void occupiedBucketClaimMakesAnotherProcessorSkipImmediately()
			throws Exception {
		MatchingBucketKey bucketKey = bucketKey();
		CountDownLatch claimed = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		TransactionTemplate transactionTemplate = new TransactionTemplate(
				transactionManager
		);

		try {
			Future<Boolean> claimHolder = executor.submit(() -> transactionTemplate.execute(
					status -> {
						boolean acquired = matchingRepository.tryClaimBucket(
								bucketKey.advisoryLockKey()
						);
						claimed.countDown();
						awaitRelease(release);
						return acquired;
					}
			));
			assertThat(claimed.await(5, TimeUnit.SECONDS)).isTrue();

			LunchMatchingBucketProcessingResult result = bucketProcessor.process(
					bucketKey,
					slot().minusMinutes(10)
			);

			assertThat(result.claimed()).isFalse();
			release.countDown();
			assertThat(claimHolder.get(5, TimeUnit.SECONDS)).isTrue();
		} finally {
			release.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void discoveryReturnsEachFutureBucketOnlyOnce() {
		createParticipant(LunchTopic.STUDY);
		createParticipant(LunchTopic.STARTUPS);
		createParticipant(LunchTopic.NETWORKING);
		MatchingBucketKey expected = bucketKey();

		List<MatchingBucketKey> futureKeys = matchingRepository.findBucketKeys(
				slot().minusMinutes(1),
				PageRequest.of(0, 1000)
		);
		List<MatchingBucketKey> expiredKeys = matchingRepository.findBucketKeys(
				slot(),
				PageRequest.of(0, 1000)
		);

		assertThat(futureKeys.stream().filter(expected::equals)).hasSize(1);
		assertThat(expiredKeys).doesNotContain(expected);
	}

	@Test
	void blockedPairDegradesWithoutStoppingTheWholeBucket() {
		List<Participant> participants = List.of(
				createParticipant(LunchTopic.STUDY),
				createParticipant(LunchTopic.STARTUPS),
				createParticipant(LunchTopic.IT_CAREER),
				createParticipant(LunchTopic.NETWORKING),
				createParticipant(LunchTopic.CASUAL_CHAT)
		);
		Participant first = participants.get(0);
		Participant second = participants.get(1);
		ProfileBlock block = new ProfileBlock();
		block.setBlockerProfileId(first.profile().getId());
		block.setBlockedProfileId(second.profile().getId());
		blockRepository.saveAndFlush(block);
		Participant excluded = List.of(first, second).stream()
				.max(Comparator
						.comparing((Participant participant) ->
								participant.request().getCreatedAt())
						.thenComparing(participant -> participant.request().getId()))
				.orElseThrow();

		LunchMatchingBucketProcessingResult result = bucketProcessor.process(
				bucketKey(),
				slot().minusMinutes(10)
		);

		assertThat(result.formedGroupCount()).isEqualTo(1);
		assertThat(result.matchedCandidateCount()).isEqualTo(4);
		assertThat(result.remainingRequestCount()).isEqualTo(1);
		assertThat(requestRepository.findById(excluded.request().getId()).orElseThrow()
				.getStatus()).isEqualTo(LunchRequestStatus.SEARCHING);
		assertThat(memberRepository.findAllByLunchRequestIds(requestIds(participants)))
				.extracting(member -> member.getProfile().getId())
				.doesNotContain(excluded.profile().getId())
				.hasSize(4);
	}

	@Test
	void restrictedCandidateIsSkippedWhileEligibleCandidatesStillMatch() {
		List<Participant> participants = List.of(
				createParticipant(LunchTopic.STUDY),
				createParticipant(LunchTopic.STARTUPS),
				createParticipant(LunchTopic.IT_CAREER),
				createParticipant(LunchTopic.NETWORKING),
				createParticipant(LunchTopic.CASUAL_CHAT)
		);
		Participant restricted = participants.get(0);
		AccountSanction sanction = new AccountSanction();
		sanction.setTargetUserId(restricted.profile().getUser().getId());
		sanction.setTargetProfileId(restricted.profile().getId());
		sanction.setType(AccountSanctionType.PERMANENT_BAN);
		sanction.setReason("Matching worker eligibility test");
		sanction.setCreatedByUserId(participants.get(1).profile().getUser().getId());
		sanctionRepository.saveAndFlush(sanction);

		LunchMatchingBucketProcessingResult result = bucketProcessor.process(
				bucketKey(),
				slot().minusMinutes(10)
		);

		assertThat(result.formedGroupCount()).isEqualTo(1);
		assertThat(result.matchedCandidateCount()).isEqualTo(4);
		assertThat(requestRepository.findById(restricted.request().getId()).orElseThrow()
				.getStatus()).isEqualTo(LunchRequestStatus.SEARCHING);
		assertThat(memberRepository.findAllByLunchRequestIds(requestIds(participants)))
				.extracting(member -> member.getProfile().getId())
				.doesNotContain(restricted.profile().getId())
				.hasSize(4);
	}

	private LunchMatchingBucketProcessingResult processAfterSignal(
			MatchingBucketKey bucketKey,
			LocalDateTime now,
			CountDownLatch ready,
			CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Matching start signal timed out");
		}
		return bucketProcessor.process(bucketKey, now);
	}

	private void awaitRelease(CountDownLatch release) {
		try {
			if (!release.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Claim release signal timed out");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Claim holder was interrupted", exception);
		}
	}

	private Participant createParticipant(LunchTopic topic) {
		User user = new User();
		user.setEmail("matching-" + UUID.randomUUID() + "@example.com");
		user.setPasswordHash("test-password-hash");
		user.setEmailVerifiedAt(LocalDateTime.now());
		user = userRepository.saveAndFlush(user);

		Profile profile = new Profile();
		profile.setDisplayName("Matching participant " + UUID.randomUUID());
		profile.setStatus(ProfileStatus.ACTIVE);
		profile.setUser(user);
		profile = profileRepository.saveAndFlush(profile);

		LunchRequest request = new LunchRequest();
		request.setProfile(profile);
		request.setLocation(location);
		request.setStatus(LunchRequestStatus.SEARCHING);
		request.setTopic(topic);
		request.setTimeSlot(slot());
		request = requestRepository.saveAndFlush(request);
		return new Participant(profile, request);
	}

	private Location createLocation() {
		University university = new University();
		university.setName("Worker University " + UUID.randomUUID());
		university.setCity("Saint Petersburg");
		university = universityRepository.saveAndFlush(university);

		Location newLocation = new Location();
		newLocation.setUniversity(university);
		newLocation.setType(LocationType.DINING_ROOM);
		newLocation.setName("Worker Canteen " + UUID.randomUUID());
		newLocation.setAddress("Kronverksky Prospekt 49");
		return locationRepository.saveAndFlush(newLocation);
	}

	private MatchingBucketKey bucketKey() {
		return new MatchingBucketKey(location.getId(), slot());
	}

	private LocalDateTime slot() {
		return LocalDateTime.of(2026, 8, 22, 13, 0);
	}

	private List<UUID> requestIds(List<Participant> participants) {
		return participants.stream()
				.map(participant -> participant.request().getId())
				.toList();
	}

	private long formationEventCount() {
		return outboxEventRepository.findAll().stream()
				.filter(event -> event.getEventType()
						== OutboxEventType.LUNCH_GROUP_FORMED)
				.count();
	}

	private record Participant(Profile profile, LunchRequest request) {
	}
}
