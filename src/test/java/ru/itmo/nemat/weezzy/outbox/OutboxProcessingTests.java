package ru.itmo.nemat.weezzy.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.notification.Notification;
import ru.itmo.nemat.weezzy.notification.NotificationRepository;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.outbox.cleanup.OutboxCleanupService;
import ru.itmo.nemat.weezzy.outbox.exception.OutboxEventClaimException;
import ru.itmo.nemat.weezzy.outbox.exception.OutboxEventHandlerNotFoundException;
import ru.itmo.nemat.weezzy.outbox.exception.InvalidOutboxEventPayloadException;
import ru.itmo.nemat.weezzy.outbox.handler.ProfileLikedEventHandler;
import ru.itmo.nemat.weezzy.outbox.worker.OutboxEventClaimService;
import ru.itmo.nemat.weezzy.outbox.worker.OutboxEventProcessor;
import ru.itmo.nemat.weezzy.security.JwtService;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.user.UserRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OutboxProcessingTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");
	private static final String WORKER_ID = "outbox-processing-test";

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
	private OutboxEventRepository eventRepository;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private OutboxEventClaimService claimService;

	@Autowired
	private OutboxEventProcessor processor;

	@Autowired
	private ProfileLikedEventHandler profileLikedEventHandler;

	@Autowired
	private OutboxCleanupService cleanupService;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private MeterRegistry meterRegistry;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void clearOutboxAndNotifications() {
		notificationRepository.deleteAll();
		eventRepository.deleteAll();
	}

	@Test
	void processesProfileLikedEventAndCreatesNotification() throws Exception {
		TestProfile source = createProfile("Processed Like Source");
		TestProfile target = createProfile("Processed Like Target");
		performVote(source, target, "LIKE");
		OutboxEvent event = onlyEvent(OutboxEventType.PROFILE_LIKED);

		claimAndProcess(event.getId(), LocalDateTime.now().plusMinutes(1));

		OutboxEvent processed = eventRepository.findById(event.getId()).orElseThrow();
		assertThat(processed.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
		assertThat(processed.getProcessedAt()).isNotNull();
		assertThat(processed.getLockedAt()).isNull();
		assertThat(processed.getLockedBy()).isNull();
		Notification notification = notificationRepository
				.findByRecipientUserIdAndSourceEventId(
						UUID.fromString(target.owner().userId()),
						event.getId()
				)
				.orElseThrow();
		assertThat(notification.getType()).isEqualTo(NotificationType.NEW_LIKE);
		assertThat(String.valueOf(notification.getPayload().get("sourceProfileId")))
				.isEqualTo(source.id());
	}

	@Test
	void canceledLikeIsProcessedWithoutCreatingNotification() throws Exception {
		TestProfile source = createProfile("Canceled Like Source");
		TestProfile target = createProfile("Canceled Like Target");
		performVote(source, target, "LIKE");
		OutboxEvent event = onlyEvent(OutboxEventType.PROFILE_LIKED);
		performVote(source, target, "PASS");

		claimAndProcess(event.getId(), LocalDateTime.now().plusMinutes(1));

		assertThat(eventRepository.findById(event.getId()).orElseThrow().getStatus())
				.isEqualTo(OutboxEventStatus.PROCESSED);
		assertThat(notificationRepository.findAll()).isEmpty();
	}

	@Test
	void matchEventCreatesTwoNotificationsAtomically() throws Exception {
		TestProfile first = createProfile("Processed Match First");
		TestProfile second = createProfile("Processed Match Second");
		performVote(first, second, "LIKE");
		performVote(second, first, "LIKE");
		OutboxEvent event = onlyEvent(OutboxEventType.MATCH_CREATED);

		claimAll(LocalDateTime.now().plusMinutes(1));
		processor.process(event.getId(), WORKER_ID, LocalDateTime.now());

		List<Notification> notifications = notificationsForEvent(event.getId());
		assertThat(notifications).hasSize(2);
		assertThat(notifications)
				.extracting(Notification::getRecipientUserId)
				.containsExactlyInAnyOrder(
						UUID.fromString(first.owner().userId()),
						UUID.fromString(second.owner().userId())
				);
		assertThat(notifications)
				.extracting(Notification::getType)
				.containsOnly(NotificationType.NEW_MATCH);
	}

	@Test
	void reportDecisionEventCreatesNotificationForReporter() throws Exception {
		AuthenticatedTestUser admin = createAdmin();
		TestProfile reporter = createProfile("Report Decision Reporter");
		TestProfile target = createProfile("Report Decision Target");
		String reportId = createReport(reporter, target);

		mockMvc.perform(admin.authorize(patch(
						"/api/admin/reports/{reportId}/decision",
						reportId
				))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "status": "RESOLVED",
							  "decision": "Violation confirmed"
							}
							"""))
				.andExpect(status().isOk());
		OutboxEvent event = onlyEvent(OutboxEventType.REPORT_DECIDED);
		assertThat(event.getPayload()).containsOnlyKeys(
				"reportId",
				"recipientUserId",
				"targetProfileId",
				"status",
				"decision"
		);
		assertThat(String.valueOf(event.getPayload().get("recipientUserId")))
				.isEqualTo(reporter.owner().userId());

		claimAndProcess(event.getId(), LocalDateTime.now().plusMinutes(1));

		Notification notification = notificationRepository
				.findByRecipientUserIdAndSourceEventId(
						UUID.fromString(reporter.owner().userId()),
						event.getId()
				)
				.orElseThrow();
		assertThat(notification.getType()).isEqualTo(NotificationType.REPORT_DECISION);
		assertThat(String.valueOf(notification.getPayload().get("reportId")))
				.isEqualTo(reportId);
		assertThat(String.valueOf(notification.getPayload().get("targetProfileId")))
				.isEqualTo(target.id());
		assertThat(notification.getPayload())
				.containsEntry("status", "RESOLVED")
				.containsEntry("decision", "Violation confirmed");
	}

	@Test
	void accountSanctionEventCreatesNotificationForTargetUser() throws Exception {
		AuthenticatedTestUser admin = createAdmin();
		TestProfile target = createProfile("Sanction Notification Target");
		String sanctionId = createPermanentSanction(admin, target);
		OutboxEvent event = onlyEvent(OutboxEventType.ACCOUNT_SANCTION_CREATED);
		assertThat(event.getPayload()).containsOnlyKeys(
				"sanctionId",
				"recipientUserId",
				"type",
				"reason",
				"expiresAt",
				"sourceReportId"
		);
		assertThat(String.valueOf(event.getPayload().get("recipientUserId")))
				.isEqualTo(target.owner().userId());

		claimAndProcess(event.getId(), LocalDateTime.now().plusMinutes(1));

		Notification notification = notificationRepository
				.findByRecipientUserIdAndSourceEventId(
						UUID.fromString(target.owner().userId()),
						event.getId()
				)
				.orElseThrow();
		assertThat(notification.getType()).isEqualTo(NotificationType.ADMIN_SANCTION);
		assertThat(String.valueOf(notification.getPayload().get("sanctionId")))
				.isEqualTo(sanctionId);
		assertThat(notification.getPayload())
				.containsEntry("type", "PERMANENT_BAN")
				.containsEntry("reason", "Repeated abuse")
				.doesNotContainKeys("expiresAt", "sourceReportId");
	}

	@Test
	void revokedSanctionIsProcessedWithoutCreatingNotification() throws Exception {
		AuthenticatedTestUser admin = createAdmin();
		TestProfile target = createProfile("Revoked Sanction Target");
		String sanctionId = createPermanentSanction(admin, target);
		OutboxEvent event = onlyEvent(OutboxEventType.ACCOUNT_SANCTION_CREATED);

		revokeSanction(admin, sanctionId, "Decision overturned");

		claimAndProcess(event.getId(), LocalDateTime.now().plusMinutes(1));

		assertThat(eventRepository.findById(event.getId()).orElseThrow().getStatus())
				.isEqualTo(OutboxEventStatus.PROCESSED);
		assertThat(notificationsForEvent(event.getId())).isEmpty();
	}

	@Test
	void sanctionRevocationEventCreatesNotificationForTargetUser() throws Exception {
		AuthenticatedTestUser admin = createAdmin();
		TestProfile target = createProfile("Sanction Revocation Target");
		String sanctionId = createPermanentSanction(admin, target);
		OutboxEvent createdEvent = onlyEvent(
				OutboxEventType.ACCOUNT_SANCTION_CREATED
		);
		claimAndProcess(createdEvent.getId(), LocalDateTime.now().plusMinutes(1));

		revokeSanction(admin, sanctionId, "Appeal accepted");
		OutboxEvent revokedEvent = onlyEvent(
				OutboxEventType.ACCOUNT_SANCTION_REVOKED
		);
		assertThat(revokedEvent.getPayload()).containsOnlyKeys(
				"sanctionId",
				"recipientUserId",
				"type",
				"revocationReason",
				"revokedAt"
		);
		assertThat(String.valueOf(revokedEvent.getPayload().get("sanctionId")))
				.isEqualTo(sanctionId);
		assertThat(String.valueOf(revokedEvent.getPayload().get("recipientUserId")))
				.isEqualTo(target.owner().userId());

		claimAndProcess(revokedEvent.getId(), LocalDateTime.now().plusMinutes(2));

		Notification notification = notificationRepository
				.findByRecipientUserIdAndSourceEventId(
						UUID.fromString(target.owner().userId()),
						revokedEvent.getId()
				)
				.orElseThrow();
		assertThat(notification.getType())
				.isEqualTo(NotificationType.ADMIN_SANCTION_REVOKED);
		assertThat(String.valueOf(notification.getPayload().get("sanctionId")))
				.isEqualTo(sanctionId);
		assertThat(notification.getPayload())
				.containsEntry("type", "PERMANENT_BAN")
				.containsEntry("revocationReason", "Appeal accepted")
				.containsKey("revokedAt");
	}

	@Test
	void mismatchedSanctionRevocationIsProcessedWithoutNotification()
			throws Exception {
		AuthenticatedTestUser admin = createAdmin();
		TestProfile target = createProfile("Mismatched Revocation Target");
		TestProfile anotherUser = createProfile("Mismatched Revocation Recipient");
		String sanctionId = createPermanentSanction(admin, target);
		revokeSanction(admin, sanctionId, "Original reason");
		UUID eventId = saveEvent(
				OutboxEventType.ACCOUNT_SANCTION_REVOKED,
				Map.of(
						"sanctionId", sanctionId,
						"recipientUserId", anotherUser.owner().userId(),
						"type", "PERMANENT_BAN",
						"revocationReason", "Tampered reason",
						"revokedAt", LocalDateTime.now().toString()
				),
				0
		);

		claimAndProcess(eventId, LocalDateTime.now().plusMinutes(1));

		assertThat(eventRepository.findById(eventId).orElseThrow().getStatus())
				.isEqualTo(OutboxEventStatus.PROCESSED);
		assertThat(notificationsForEvent(eventId)).isEmpty();
	}

	@Test
	void expiredSanctionIsProcessedWithoutCreatingNotification() throws Exception {
		AuthenticatedTestUser admin = createAdmin();
		TestProfile target = createProfile("Expired Sanction Target");
		String sanctionId = createTemporarySanction(admin, target);
		OutboxEvent event = onlyEvent(OutboxEventType.ACCOUNT_SANCTION_CREATED);
		jdbcTemplate.update(
				"UPDATE account_sanctions SET expires_at = ? WHERE id = ?",
				LocalDateTime.now().minusMinutes(1),
				UUID.fromString(sanctionId)
		);

		claimAndProcess(event.getId(), LocalDateTime.now().plusMinutes(1));

		assertThat(eventRepository.findById(event.getId()).orElseThrow().getStatus())
				.isEqualTo(OutboxEventStatus.PROCESSED);
		assertThat(notificationsForEvent(event.getId())).isEmpty();
	}

	@Test
	void missingReportIsProcessedWithoutCreatingNotification() throws Exception {
		TestProfile reporter = createProfile("Missing Report Recipient");
		TestProfile target = createProfile("Missing Report Target");
		UUID eventId = saveEvent(
				OutboxEventType.REPORT_DECIDED,
				Map.of(
						"reportId", UUID.randomUUID(),
						"recipientUserId", reporter.owner().userId(),
						"targetProfileId", target.id(),
						"status", "RESOLVED",
						"decision", "Missing report decision"
				),
				0
		);

		claimAndProcess(eventId, LocalDateTime.now().plusMinutes(1));

		assertThat(eventRepository.findById(eventId).orElseThrow().getStatus())
				.isEqualTo(OutboxEventStatus.PROCESSED);
		assertThat(notificationsForEvent(eventId)).isEmpty();
	}

	@Test
	void handlerIsIdempotentForSameSourceEvent() throws Exception {
		TestProfile source = createProfile("Idempotent Like Source");
		TestProfile target = createProfile("Idempotent Like Target");
		performVote(source, target, "LIKE");
		OutboxEvent event = onlyEvent(OutboxEventType.PROFILE_LIKED);

		profileLikedEventHandler.handle(event);
		profileLikedEventHandler.handle(event);

		assertThat(notificationsForEvent(event.getId())).hasSize(1);
	}

	@Test
	void handlerFailureRollsBackNotificationsAndEventCompletion() throws Exception {
		TestProfile first = createProfile("Atomic Match First");
		TestProfile second = createProfile("Atomic Match Second");
		performVote(first, second, "LIKE");
		performVote(second, first, "LIKE");
		eventRepository.deleteAll();

		UUID eventId = saveEvent(
				OutboxEventType.MATCH_CREATED,
				Map.of(
						"firstProfileId", first.id(),
						"firstUserId", UUID.randomUUID().toString(),
						"secondProfileId", second.id(),
						"secondUserId", second.owner().userId()
				),
				0
		);
		LocalDateTime now = LocalDateTime.now().plusMinutes(1);
		claimAll(now);

		assertThatThrownBy(() -> processor.process(eventId, WORKER_ID, now))
				.isInstanceOf(RuntimeException.class);

		assertThat(notificationsForEvent(eventId)).isEmpty();
		OutboxEvent claimed = eventRepository.findById(eventId).orElseThrow();
		assertThat(claimed.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
		assertThat(claimed.getLockedBy()).isEqualTo(WORKER_ID);
	}

	@Test
	void failedProcessingSchedulesConfiguredRetry() {
		UUID eventId = saveEvent(OutboxEventType.REPORT_DECIDED, Map.of(), 0);
		LocalDateTime claimedAt = LocalDateTime.now().plusMinutes(1);
		claimAll(claimedAt);
		OutboxEventHandlerNotFoundException exception = new OutboxEventHandlerNotFoundException(
				OutboxEventType.REPORT_DECIDED
		);

		processor.recordFailure(eventId, WORKER_ID, exception, claimedAt);

		OutboxEvent event = eventRepository.findById(eventId).orElseThrow();
		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(event.getAttemptCount()).isEqualTo(1);
		assertThat(event.getNextAttemptAt())
				.isCloseTo(claimedAt.plusMinutes(1), within(1, ChronoUnit.MILLIS));
		assertThat(event.getLastError())
				.contains("OutboxEventHandlerNotFoundException")
				.contains("REPORT_DECIDED");
		assertThat(event.getLockedBy()).isNull();
	}

	@Test
	void invalidPayloadFailsProcessingAndCanBeRetried() {
		UUID eventId = saveEvent(OutboxEventType.PROFILE_LIKED, Map.of(), 0);
		LocalDateTime now = LocalDateTime.now().plusMinutes(1);
		claimAll(now);

		assertThatThrownBy(() -> processor.process(eventId, WORKER_ID, now))
				.isInstanceOf(InvalidOutboxEventPayloadException.class);
		processor.recordFailure(
				eventId,
				WORKER_ID,
				new InvalidOutboxEventPayloadException(
						eventId,
						OutboxEventType.PROFILE_LIKED,
						new IllegalArgumentException("missing fields")
				),
				now
		);

		OutboxEvent event = eventRepository.findById(eventId).orElseThrow();
		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(event.getLastError()).contains("InvalidOutboxEventPayloadException");
	}

	@Test
	void claimBatchHonorsLimitAndDoesNotClaimEventsTwice() {
		UUID first = saveEvent(OutboxEventType.REPORT_DECIDED, Map.of(), 0);
		UUID second = saveEvent(OutboxEventType.REPORT_DECIDED, Map.of(), 0);
		UUID third = saveEvent(OutboxEventType.REPORT_DECIDED, Map.of(), 0);
		LocalDateTime now = LocalDateTime.now().plusMinutes(1);

		List<UUID> firstBatch = claimService.claimBatch("worker-a", 2, now);
		List<UUID> secondBatch = claimService.claimBatch("worker-b", 2, now);

		assertThat(firstBatch).hasSize(2);
		assertThat(secondBatch).hasSize(1);
		assertThat(firstBatch).doesNotContainAnyElementsOf(secondBatch);
		assertThat(firstBatch.stream().toList())
				.containsExactlyInAnyOrderElementsOf(
						List.of(first, second, third).stream()
								.filter(id -> !secondBatch.contains(id))
								.toList()
				);
	}

	@Test
	void eventBecomesFailedAfterMaximumAttemptsAndErrorIsTruncated() {
		UUID eventId = saveEvent(OutboxEventType.REPORT_DECIDED, Map.of(), 4);
		LocalDateTime now = LocalDateTime.now().plusMinutes(1);
		claimAll(now);

		processor.recordFailure(
				eventId,
				WORKER_ID,
				new IllegalStateException("x".repeat(3000)),
				now
		);

		OutboxEvent event = eventRepository.findById(eventId).orElseThrow();
		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
		assertThat(event.getAttemptCount()).isEqualTo(5);
		assertThat(event.getLastError()).hasSize(OutboxEvent.LAST_ERROR_MAX_LENGTH);
		assertThat(event.getLockedAt()).isNull();
	}

	@Test
	void staleClaimIsRecoveredWithoutResettingAttempts() {
		UUID eventId = saveEvent(OutboxEventType.REPORT_DECIDED, Map.of(), 0);
		LocalDateTime claimedAt = LocalDateTime.now().plusMinutes(1);
		claimAll(claimedAt);
		LocalDateTime recoveredAt = claimedAt.plusMinutes(10);

		int recovered = claimService.recoverStaleClaims(
				recoveredAt.minusMinutes(5),
				10,
				recoveredAt
		);

		OutboxEvent event = eventRepository.findById(eventId).orElseThrow();
		assertThat(recovered).isEqualTo(1);
		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(event.getAttemptCount()).isEqualTo(1);
		assertThat(event.getNextAttemptAt())
				.isCloseTo(recoveredAt.plusMinutes(1), within(1, ChronoUnit.MILLIS));
		assertThat(event.getLastError()).contains("Worker lock expired");
		assertThat(event.getLockedBy()).isNull();
	}

	@Test
	void staleClaimBecomesFailedWhenAttemptsAreExhausted() {
		UUID eventId = saveEvent(OutboxEventType.REPORT_DECIDED, Map.of(), 4);
		LocalDateTime claimedAt = LocalDateTime.now().plusMinutes(1);
		claimAll(claimedAt);
		LocalDateTime recoveredAt = claimedAt.plusMinutes(10);

		claimService.recoverStaleClaims(
				recoveredAt.minusMinutes(5),
				10,
				recoveredAt
		);

		OutboxEvent event = eventRepository.findById(eventId).orElseThrow();
		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
		assertThat(event.getAttemptCount()).isEqualTo(5);
		assertThat(event.getLastError()).contains("Worker lock expired");
		assertThat(event.getLockedBy()).isNull();
	}

	@Test
	void processorRejectsClaimOwnedByAnotherWorker() {
		UUID eventId = saveEvent(OutboxEventType.REPORT_DECIDED, Map.of(), 0);
		LocalDateTime now = LocalDateTime.now().plusMinutes(1);
		claimAll(now);

		assertThatThrownBy(() -> processor.process(eventId, "another-worker", now))
				.isInstanceOf(OutboxEventClaimException.class)
				.hasMessageContaining(WORKER_ID);
	}

	@Test
	void cleanupDeletesOnlyOldProcessedEvents() {
		LocalDateTime now = LocalDateTime.now();
		UUID oldProcessed = saveProcessedEvent(now.minusDays(8));
		UUID recentProcessed = saveProcessedEvent(now.minusDays(1));
		UUID pending = saveEvent(OutboxEventType.REPORT_DECIDED, Map.of(), 0);

		int deleted = cleanupService.deleteProcessedBefore(now.minusDays(7), 100);

		assertThat(deleted).isEqualTo(1);
		assertThat(eventRepository.existsById(oldProcessed)).isFalse();
		assertThat(eventRepository.existsById(recentProcessed)).isTrue();
		assertThat(eventRepository.existsById(pending)).isTrue();
	}

	@Test
	void pendingAndFailedMetricsReflectDatabaseState() {
		saveEvent(OutboxEventType.REPORT_DECIDED, Map.of(), 0);
		UUID failedId = saveEvent(OutboxEventType.REPORT_DECIDED, Map.of(), 4);
		LocalDateTime now = LocalDateTime.now().plusMinutes(1);
		claimAll(now);
		processor.recordFailure(
				failedId,
				WORKER_ID,
				new IllegalStateException("failed"),
				now
		);

		double pending = meterRegistry.get("weezzy.outbox.events")
				.tag("status", "pending")
				.gauge()
				.value();
		double failed = meterRegistry.get("weezzy.outbox.events")
				.tag("status", "failed")
				.gauge()
				.value();
		assertThat(pending).isZero();
		assertThat(failed).isEqualTo(1);
	}

	private void claimAndProcess(UUID eventId, LocalDateTime now) {
		assertThat(claimAll(now)).contains(eventId);
		processor.process(eventId, WORKER_ID, now);
	}

	private List<UUID> claimAll(LocalDateTime now) {
		return claimService.claimBatch(WORKER_ID, 100, now);
	}

	private OutboxEvent onlyEvent(OutboxEventType eventType) {
		return eventRepository.findAll().stream()
				.filter(event -> event.getEventType() == eventType)
				.findFirst()
				.orElseThrow();
	}

	private List<Notification> notificationsForEvent(UUID eventId) {
		return notificationRepository.findAll().stream()
				.filter(notification -> eventId.equals(notification.getSourceEventId()))
				.toList();
	}

	private UUID saveEvent(
			OutboxEventType eventType,
			Map<String, Object> payload,
			int attemptCount
	) {
		return transactionTemplate.execute(status -> {
			OutboxEvent event = new OutboxEvent();
			event.setEventType(eventType);
			event.setPayload(payload);
			event.setAttemptCount(attemptCount);
			return eventRepository.saveAndFlush(event).getId();
		});
	}

	private UUID saveProcessedEvent(LocalDateTime processedAt) {
		UUID eventId = saveEvent(OutboxEventType.REPORT_DECIDED, Map.of(), 0);
		transactionTemplate.executeWithoutResult(status -> {
			OutboxEvent event = eventRepository.findById(eventId).orElseThrow();
			event.markProcessed(processedAt);
		});
		return eventId;
	}

	private TestProfile createProfile(String displayName) throws Exception {
		return AuthenticatedTestUser.register(mockMvc, objectMapper)
				.createProfile(displayName);
	}

	private AuthenticatedTestUser createAdmin() throws Exception {
		return AuthenticatedTestUser.registerAdmin(
				mockMvc,
				objectMapper,
				userRepository,
				jwtService
		);
	}

	private String createReport(TestProfile reporter, TestProfile target)
			throws Exception {
		String response = mockMvc.perform(reporter.owner().authorize(post(
						"/api/reports/{targetProfileId}",
						target.id()
				))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "reason": "SPAM",
							  "comment": "Repeated unwanted messages"
							}
							"""))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response).path("id").asText();
	}

	private String createPermanentSanction(
			AuthenticatedTestUser admin,
			TestProfile target
	) throws Exception {
		String response = mockMvc.perform(admin.authorize(post(
						"/api/admin/users/{targetUserId}/sanctions",
						target.owner().userId()
				))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "type": "PERMANENT_BAN",
							  "reason": "Repeated abuse"
							}
							"""))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response).path("id").asText();
	}

	private String createTemporarySanction(
			AuthenticatedTestUser admin,
			TestProfile target
	) throws Exception {
		String expiresAt = LocalDateTime.now().plusDays(7).toString();
		String response = mockMvc.perform(admin.authorize(post(
						"/api/admin/users/{targetUserId}/sanctions",
						target.owner().userId()
				))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "type": "TEMPORARY_SUSPENSION",
							  "reason": "Temporary restriction",
							  "expiresAt": "%s"
							}
							""".formatted(expiresAt)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response).path("id").asText();
	}

	private void revokeSanction(
			AuthenticatedTestUser admin,
			String sanctionId,
			String reason
	) throws Exception {
		mockMvc.perform(admin.authorize(patch(
						"/api/admin/sanctions/{sanctionId}/revoke",
						sanctionId
				))
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
							"reason",
							reason
					))))
				.andExpect(status().isOk());
	}

	private void performVote(TestProfile source, TestProfile target, String action)
			throws Exception {
		mockMvc.perform(source.owner().authorize(post("/api/votes/" + target.id()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "action": "%s"
								}
								""".formatted(action)))
				.andExpect(status().isOk());
	}
}
