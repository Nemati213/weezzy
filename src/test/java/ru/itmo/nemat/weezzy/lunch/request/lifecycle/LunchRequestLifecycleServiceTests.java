package ru.itmo.nemat.weezzy.lunch.request.lifecycle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import ru.itmo.nemat.weezzy.notification.Notification;
import ru.itmo.nemat.weezzy.notification.NotificationRepository;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventRepository;
import ru.itmo.nemat.weezzy.outbox.OutboxEventStatus;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.handler.LunchExtensionRequestedEventHandler;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

@Testcontainers
@Import(LunchRequestLifecycleServiceTests.FixedClockConfiguration.class)
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false",
		"app.lunch.lifecycle.enabled=false",
		"app.outbox.worker.enabled=false",
		"app.outbox.cleanup.enabled=false"
})
class LunchRequestLifecycleServiceTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");
	private static final ZoneId ZONE_ID = ZoneId.of("Europe/Moscow");
	private static final LocalDateTime NOW = LocalDateTime.of(
			2026,
			8,
			22,
			12,
			30
	);

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private LunchRequestLifecycleService lifecycleService;

	@Autowired
	private LunchExtensionRequestedEventHandler eventHandler;

	@Autowired
	private LunchRequestRepository requestRepository;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private UserRepository userRepository;

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
	void dueRequestReceivesOneIdempotentExtensionOfferAndNotification() {
		Participant participant = createParticipant(NOW, 0);

		List<UUID> offered = lifecycleService.offerExtensions(NOW, 100);
		LunchRequest request = requestRepository.findById(
				participant.request().getId()
		).orElseThrow();
		OutboxEvent event = extensionEvent(request.getId());

		assertThat(offered).containsExactly(request.getId());
		assertThat(request.getStatus())
				.isEqualTo(LunchRequestStatus.EXTENSION_REQUESTED);
		assertThat(request.getExtensionOfferId()).isNotNull();
		assertThat(request.getExtensionRequestedAt()).isEqualTo(NOW);
		assertThat(request.getExtensionExpiresAt()).isEqualTo(NOW.plusMinutes(5));
		assertThat(request.getExtensionTargetTimeSlot())
				.isEqualTo(NOW.plusMinutes(10));
		assertThat(request.getExtensionCount()).isZero();
		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(event.getPayload()).containsOnlyKeys("requestId", "offerId");
		assertThat(String.valueOf(event.getPayload().get("offerId")))
				.isEqualTo(request.getExtensionOfferId().toString());

		assertThat(lifecycleService.offerExtensions(NOW.plusMinutes(1), 100))
				.isEmpty();
		assertThat(extensionEvents(request.getId())).hasSize(1);

		eventHandler.handle(event);
		eventHandler.handle(event);
		Notification notification = notificationRepository
				.findByRecipientUserIdAndSourceEventId(
						participant.user().getId(),
						event.getId()
				).orElseThrow();
		assertThat(notification.getType())
				.isEqualTo(NotificationType.LUNCH_EXTENSION_REQUESTED);
		assertThat(notification.getPayload()).containsOnlyKeys(
				"requestId",
				"offerId",
				"timeSlot",
				"targetTimeSlot",
				"expiresAt",
				"topic"
		);
		assertThat(notificationRepository.findAll().stream()
				.filter(candidate -> candidate.getSourceEventId().equals(event.getId())))
				.hasSize(1);
	}

	@Test
	void limitAndWindowEndDoNotCreateInvalidOffersOrStarveValidRequest() {
		Participant previousDay = createParticipant(
				LocalDateTime.of(2026, 8, 21, 12, 0),
				0
		);
		Participant reachedLimit = createParticipant(
				LocalDateTime.of(2026, 8, 22, 14, 20),
				2
		);
		Participant tooLate = createParticipant(
				LocalDateTime.of(2026, 8, 22, 14, 55),
				0
		);
		Participant valid = createParticipant(
				LocalDateTime.of(2026, 8, 22, 14, 30),
				1
		);
		LocalDateTime processingTime = LocalDateTime.of(2026, 8, 22, 15, 0);

		List<UUID> offered = lifecycleService.offerExtensions(processingTime, 1);

		assertThat(offered).containsExactly(valid.request().getId());
		assertThat(requestStatus(previousDay))
				.isEqualTo(LunchRequestStatus.SEARCHING);
		assertThat(requestStatus(reachedLimit)).isEqualTo(LunchRequestStatus.SEARCHING);
		assertThat(requestStatus(tooLate)).isEqualTo(LunchRequestStatus.SEARCHING);
		assertThat(requestStatus(valid))
				.isEqualTo(LunchRequestStatus.EXTENSION_REQUESTED);
		assertThat(extensionEvents(reachedLimit.request().getId())).isEmpty();
		assertThat(extensionEvents(tooLate.request().getId())).isEmpty();
		assertThat(extensionEvents(previousDay.request().getId())).isEmpty();
	}

	@Test
	void parallelBatchesCreateExactlyOneOfferPerRequest() throws Exception {
		List<Participant> participants = new ArrayList<>();
		for (int index = 0; index < 20; index++) {
			participants.add(createParticipant(NOW, 0));
		}
		Set<UUID> requestIds = participants.stream()
				.map(participant -> participant.request().getId())
				.collect(java.util.stream.Collectors.toSet());
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<List<UUID>> first = executor.submit(() -> offerAfterSignal(
					ready,
					start
			));
			Future<List<UUID>> second = executor.submit(() -> offerAfterSignal(
					ready,
					start
			));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<UUID> offered = new ArrayList<>(first.get(10, TimeUnit.SECONDS));
			offered.addAll(second.get(10, TimeUnit.SECONDS));
			assertThat(offered).containsExactlyInAnyOrderElementsOf(requestIds);
			assertThat(new HashSet<>(offered)).hasSize(20);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(requestRepository.findAllById(requestIds))
				.extracting(LunchRequest::getStatus)
				.containsOnly(LunchRequestStatus.EXTENSION_REQUESTED);
		assertThat(requestIds).allSatisfy(requestId ->
				assertThat(extensionEvents(requestId)).hasSize(1)
		);
	}

	@Test
	void staleOrExpiredOfferEventDoesNotNotify() {
		Participant staleParticipant = createParticipant(NOW, 0);
		lifecycleService.offerExtensions(NOW, 100);
		LunchRequest staleRequest = requestRepository.findById(
				staleParticipant.request().getId()
		).orElseThrow();
		OutboxEvent staleEvent = extensionEvent(staleRequest.getId());
		staleRequest.setExtensionOfferId(UUID.randomUUID());
		requestRepository.saveAndFlush(staleRequest);

		Participant expiredParticipant = createParticipant(NOW.minusMinutes(10), 0);
		lifecycleService.offerExtensions(NOW.minusMinutes(10), 100);
		OutboxEvent expiredEvent = extensionEvent(expiredParticipant.request().getId());

		eventHandler.handle(staleEvent);
		eventHandler.handle(expiredEvent);

		assertThat(notificationRepository.findByRecipientUserIdAndSourceEventId(
				staleParticipant.user().getId(),
				staleEvent.getId()
		)).isEmpty();
		assertThat(notificationRepository.findByRecipientUserIdAndSourceEventId(
				expiredParticipant.user().getId(),
				expiredEvent.getId()
		)).isEmpty();
	}

	private List<UUID> offerAfterSignal(
			CountDownLatch ready,
			CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Lifecycle start signal timed out");
		}
		return lifecycleService.offerExtensions(NOW, 100);
	}

	private Participant createParticipant(
			LocalDateTime timeSlot,
			int extensionCount
	) {
		User user = new User();
		user.setEmail("lifecycle-" + UUID.randomUUID() + "@example.com");
		user.setPasswordHash("test-password-hash");
		user.setEmailVerifiedAt(NOW.minusDays(1));
		user = userRepository.saveAndFlush(user);

		Profile profile = new Profile();
		profile.setDisplayName("Lifecycle participant " + UUID.randomUUID());
		profile.setStatus(ProfileStatus.ACTIVE);
		profile.setUser(user);
		profile = profileRepository.saveAndFlush(profile);

		LunchRequest request = new LunchRequest();
		request.setProfile(profile);
		request.setLocation(location);
		request.setStatus(LunchRequestStatus.SEARCHING);
		request.setTopic(LunchTopic.STUDY);
		request.setTimeSlot(timeSlot);
		request.setExtensionCount(extensionCount);
		request = requestRepository.saveAndFlush(request);
		return new Participant(user, request);
	}

	private LunchRequestStatus requestStatus(Participant participant) {
		return requestRepository.findById(participant.request().getId())
				.orElseThrow()
				.getStatus();
	}

	private OutboxEvent extensionEvent(UUID requestId) {
		List<OutboxEvent> events = extensionEvents(requestId);
		assertThat(events).hasSize(1);
		return events.getFirst();
	}

	private List<OutboxEvent> extensionEvents(UUID requestId) {
		return outboxEventRepository.findAll().stream()
				.filter(event -> event.getEventType()
						== OutboxEventType.LUNCH_EXTENSION_REQUESTED)
				.filter(event -> String.valueOf(event.getPayload().get("requestId"))
						.equals(requestId.toString()))
				.toList();
	}

	private Location createLocation() {
		University university = new University();
		university.setName("Lifecycle University " + UUID.randomUUID());
		university.setCity("Saint Petersburg");
		university = universityRepository.saveAndFlush(university);

		Location newLocation = new Location();
		newLocation.setUniversity(university);
		newLocation.setType(LocationType.DINING_ROOM);
		newLocation.setName("Lifecycle Canteen " + UUID.randomUUID());
		newLocation.setAddress("Kronverksky Prospekt 49");
		return locationRepository.saveAndFlush(newLocation);
	}

	private record Participant(User user, LunchRequest request) {
	}

	@TestConfiguration
	static class FixedClockConfiguration {
		@Bean
		@Primary
		Clock lifecycleTestClock() {
			return Clock.fixed(NOW.atZone(ZONE_ID).toInstant(), ZONE_ID);
		}
	}
}
