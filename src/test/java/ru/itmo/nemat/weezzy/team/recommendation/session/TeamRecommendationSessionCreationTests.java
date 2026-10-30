package ru.itmo.nemat.weezzy.team.recommendation.session;

import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.ConstraintViolationException;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.common.exception.ForbiddenException;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.team.position.skill.TeamPositionSkillRepository;
import ru.itmo.nemat.weezzy.team.recommendation.candidate.TeamRecommendationCandidateRepository;
import ru.itmo.nemat.weezzy.team.recommendation.impression.TeamRecommendationImpressionRepository;
import ru.itmo.nemat.weezzy.team.recommendation.impression.TeamRecommendationImpressionService;
import ru.itmo.nemat.weezzy.team.recommendation.page.TeamRecommendationPageService;
import ru.itmo.nemat.weezzy.team.recommendation.ranking.TeamPositionRanker;
import ru.itmo.nemat.weezzy.user.UserNotFoundException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static ru.itmo.nemat.weezzy.support.TeamRecommendationTestData.upsertImpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false", "app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false", "app.team-recommendation.max-active-sessions-per-user=3"
})
class TeamRecommendationSessionCreationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));

	@Autowired private TeamRecommendationSessionService service;
	@Autowired private TeamRecommendationSessionRepository sessions;
	@Autowired private TeamRecommendationEntryRepository entries;
	@Autowired private TeamRecommendationImpressionRepository impressions;
	@Autowired private TeamRecommendationImpressionService impressionService;
	@Autowired private TeamRecommendationPageService pages;
	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private EntityManagerFactory entityManagerFactory;
	@Autowired private PlatformTransactionManager transactionManager;
	@MockitoSpyBean private JdbcTemplate jdbc;
	@MockitoSpyBean private TeamRecommendationOrderService order;
	@MockitoSpyBean private TeamRecommendationSessionWriteRepository writer;
	@MockitoSpyBean private TeamRecommendationCandidateRepository candidates;
	@MockitoSpyBean private TeamPositionSkillRepository skills;
	@MockitoSpyBean private TeamPositionRanker ranker;
	@MockitoBean private Clock clock;

	private UUID viewer;
	private UUID ownerProfile;
	private LocalDateTime now;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void setUp() throws Exception {
		now = LocalDateTime.now().withNano(0);
		setTime(now);
		jdbc.update("DELETE FROM team_requests");
		viewer = UUID.fromString(AuthenticatedTestUser.register(mockMvc, objectMapper).userId());
		TestProfile owner = AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile("Owner");
		ownerProfile = UUID.fromString(owner.id());
		jdbc.update("UPDATE profiles SET status = 'ACTIVE' WHERE id = ?", ownerProfile);
	}

	@Test
	void createsCompleteOrderWithoutDuplicateTeamsOrDeliveryMarks() {
		Seed first = team();
		Seed second = team();
		UUID extraRequest = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO team_requests (id, team_id, title, recruitment_deadline)
				VALUES (?, ?, 'Other publication', ?)
				""", extraRequest, first.team(), now.plusDays(1));
		jdbc.update("""
				INSERT INTO team_request_positions (id, request_id, team_position_id)
				VALUES (?, ?, ?)
				""", UUID.randomUUID(), extraRequest, first.position());
		var result = create(UUID.randomUUID());
		assertThat(result.created()).isTrue();
		assertThat(result.entryCount()).isEqualTo(2);
		assertThat(result.pageSize()).isEqualTo(20);
		assertThat(result.createdAt()).isEqualTo(now);
		assertThat(result.expiresAt()).isEqualTo(now.plusDays(1));
		var saved = entries.findWindow(result.sessionId(), 0, 20);
		assertThat(saved).extracting(TeamRecommendationEntry::getTeamId)
				.containsExactlyInAnyOrder(first.team(), second.team());
		assertThat(saved).extracting(TeamRecommendationEntry::getOrdinal).containsExactly(0, 1);
		assertThat(saved).allSatisfy(entry -> {
			assertThat(entry.getFirstDeliveredAt()).isNull();
			assertThat(entry.getFirstShownAt()).isNull();
		});
	}

	@Test
	void emptyCatalogStillCreatesRestorableSession() {
		var result = create(UUID.randomUUID());
		assertThat(result.entryCount()).isZero();
		assertThat(entries.findWindow(result.sessionId(), 0, 20)).isEmpty();
	}

	@Test
	void retryPreservesOrderExpiryAndDoesNotRerankOrEvict() {
		Seed seed = team();
		UUID key = UUID.randomUUID();
		var original = create(key);
		create(UUID.randomUUID());
		create(UUID.randomUUID());
		var originalEntries = entries.findWindow(original.sessionId(), 0, 20);
		jdbc.update("UPDATE team_requests SET status = 'CLOSED' WHERE id = ?", seed.request());
		team();
		setTime(now.plusHours(1));
		clearInvocations(order, writer);
		var repeated = create(key);
		assertThat(repeated.created()).isFalse();
		assertThat(repeated.sessionId()).isEqualTo(original.sessionId());
		assertThat(repeated.expiresAt()).isEqualTo(original.expiresAt());
		assertThat(entries.findWindow(original.sessionId(), 0, 20))
				.extracting(TeamRecommendationEntry::getId)
				.containsExactlyElementsOf(originalEntries.stream().map(TeamRecommendationEntry::getId).toList());
		assertThat(sessions.findActiveByOwner(viewer, now)).hasSize(3);
		verify(order, never()).build(any(), any());
		verify(writer, never()).insertEntries(any(), anyList());
		assertThatThrownBy(() -> service.createForUser(viewer, key, 10))
				.isInstanceOf(TeamRecommendationSessionConflictException.class);
	}

	@Test
	void expiredKeyReturnsNotFoundWithoutRecreatingSession() {
		UUID key = UUID.randomUUID();
		var original = create(key);
		setTime(original.expiresAt());
		assertThatThrownBy(() -> create(key)).isInstanceOf(TeamRecommendationSessionNotFoundException.class);
		assertThat(sessions.findByOwnerUserIdAndClientRequestId(viewer, key)).get()
				.extracting(TeamRecommendationSession::getId).isEqualTo(original.sessionId());
		assertThat(create(UUID.randomUUID()).created()).isTrue();
	}

	@Test
	void evictionUsesOldestCreationThenUuidAndPreservesImpressions() {
		Seed seed = team();
		var first = create(UUID.randomUUID());
		var second = create(UUID.randomUUID());
		var third = create(UUID.randomUUID());
		UUID oldest = List.of(first.sessionId(), second.sessionId(), third.sessionId()).stream()
				.min(Comparator.comparing(UUID::toString)).orElseThrow();
		upsertImpression(jdbc, viewer, seed.team(), now);
		setTime(now.plusSeconds(1));
		var added = create(UUID.randomUUID());
		assertThat(sessions.findById(oldest)).isEmpty();
		assertThat(entries.findWindow(oldest, 0, 20)).isEmpty();
		assertThat(sessions.findById(added.sessionId())).isPresent();
		assertThat(sessions.findActiveByOwner(viewer, now)).hasSize(3);
		assertThat(impressions.findByOwnerUserIdAndTeamIdIn(viewer, List.of(seed.team()))).hasSize(1);
	}

	@Test
	void failureAfterBatchInsertRollsBackNewSessionAndPreservesOldOnes() {
		team();
		var first = create(UUID.randomUUID());
		var second = create(UUID.randomUUID());
		var third = create(UUID.randomUUID());
		UUID key = UUID.randomUUID();
		long entryCount = countEntries();
		doAnswer(invocation -> {
			invocation.callRealMethod();
			throw new IllegalStateException("Simulated failure after insert");
		}).when(AopTestUtils.<TeamRecommendationSessionWriteRepository>getUltimateTargetObject(writer))
				.insertEntries(any(), anyList());
		assertThatThrownBy(() -> create(key)).hasRootCauseInstanceOf(IllegalStateException.class)
				.hasRootCauseMessage("Simulated failure after insert");
		assertThat(sessions.findByOwnerUserIdAndClientRequestId(viewer, key)).isEmpty();
		assertThat(countEntries()).isEqualTo(entryCount);
		assertThat(sessions.findActiveByOwner(viewer, now)).extracting(TeamRecommendationSession::getId)
				.containsExactlyInAnyOrder(first.sessionId(), second.sessionId(), third.sessionId());
	}

	@Test
	void concurrentSameKeyCreatesOneSessionAndOneOrder() throws Exception {
		team();
		UUID key = UUID.randomUUID();
		var results = concurrent(key, key);
		assertThat(results).extracting(TeamRecommendationSessionResult::sessionId)
				.containsOnly(results.getFirst().sessionId());
		assertThat(results.stream().filter(TeamRecommendationSessionResult::created)).hasSize(1);
		assertThat(sessions.findActiveByOwner(viewer, now)).hasSize(1);
		assertThat(entries.findWindow(results.getFirst().sessionId(), 0, 20)).hasSize(1);
	}

	@Test
	void concurrentDifferentKeysCannotExceedActiveLimit() throws Exception {
		team();
		create(UUID.randomUUID());
		create(UUID.randomUUID());
		setTime(now.plusMinutes(1));
		assertThat(concurrent(UUID.randomUUID(), UUID.randomUUID()))
				.allMatch(TeamRecommendationSessionResult::created);
		assertThat(sessions.findActiveByOwner(viewer, now)).hasSize(3);
	}

	@Test
	void validationAndMissingAccountDoNotCreateSessions() {
		assertThatThrownBy(() -> service.createForUser(viewer, UUID.randomUUID(), 0))
				.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> service.createForUser(viewer, UUID.randomUUID(), 51))
				.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> service.createForUser(viewer, null, 20))
				.isInstanceOf(ConstraintViolationException.class);
		assertThatThrownBy(() -> service.createForUser(UUID.randomUUID(), UUID.randomUUID(), 20))
				.isInstanceOf(UserNotFoundException.class);
		assertThat(sessions.findActiveByOwner(viewer, now)).isEmpty();
	}

	@Test
	void sanctionedAccountCannotCreateOrReplaySession() {
		UUID key = UUID.randomUUID();
		create(key);
		jdbc.update("""
				INSERT INTO account_sanctions
				(id, target_user_id, type, status, reason, created_by_user_id, created_at, updated_at)
				VALUES (?, ?, 'PERMANENT_BAN', 'ACTIVE', 'Test', ?, ?, ?)
				""", UUID.randomUUID(), viewer, viewer, now, now);
		assertThatThrownBy(() -> create(key)).isInstanceOf(ForbiddenException.class);
		assertThatThrownBy(() -> create(UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
		assertThat(sessions.findActiveByOwner(viewer, now)).hasSize(1);
	}

	@Test
	void realSkillProjectionRanksCoverageBeforeFreshness() throws Exception {
		var applicant = AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile("Applicant");
		viewer = UUID.fromString(applicant.owner().userId());
		UUID matched = UUID.randomUUID();
		UUID missing = UUID.randomUUID();
		jdbc.update("INSERT INTO skills (id, name, created_at) VALUES (?, ?, ?), (?, ?, ?)",
				matched, matched.toString(), now, missing, missing.toString(), now);
		jdbc.update("INSERT INTO profile_skills (profile_id, skill_id, created_at) VALUES (?, ?, ?)",
				UUID.fromString(applicant.id()), matched, now);
		Seed exact = team();
		Seed partial = team();
		jdbc.update("UPDATE team_requests SET created_at = ? WHERE id = ?", now.minusDays(1), exact.request());
		jdbc.update("""
				INSERT INTO team_position_skills (position_id, skill_id) VALUES (?, ?), (?, ?), (?, ?)
				""", exact.position(), matched, partial.position(), matched, partial.position(), missing);
		var result = create(UUID.randomUUID());
		assertThat(entries.findWindow(result.sessionId(), 0, 20))
				.extracting(TeamRecommendationEntry::getTeamId).containsExactly(exact.team(), partial.team());
	}

	@Test
	void readingAndWritingMoreThanOneThousandTeamsUsesBatchesWithoutEntityGraphs() {
		for (int i = 0; i < 1001; i++) {
			team();
		}
		clearInvocations(candidates, skills, jdbc);
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		try {
			statistics.clear();
			var result = create(UUID.randomUUID());
			assertThat(result.entryCount()).isEqualTo(1001);
			assertThat(statistics.getEntityLoadCount()).isLessThanOrEqualTo(3);
			assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(20);
			verify(candidates, times(3)).findEligible(any(), any(), any(), any(), any());
			verify(skills, times(3)).findSkillIdsByPositionIds(any());
			verify(jdbc, times(3)).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
			var saved = entries.findWindow(result.sessionId(), 0, 2000);
			assertThat(saved).hasSize(1001);
			assertThat(saved.stream().map(TeamRecommendationEntry::getTeamId).distinct()).hasSize(1001);
			for (int i = 0; i < saved.size(); i++) {
				assertThat(saved.get(i).getOrdinal()).isEqualTo(i);
			}
		} finally {
			statistics.setStatisticsEnabled(false);
		}
	}

	@Test
	void candidateBatchesShareOneRepeatableReadSnapshot() {
		for (int i = 0; i < 501; i++) {
			team();
		}
		UUID lastRequest = jdbc.queryForObject("""
				SELECT request_id FROM team_request_positions ORDER BY id DESC LIMIT 1
				""", UUID.class);
		var otherTransaction = new TransactionTemplate(transactionManager);
		otherTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		AtomicInteger reads = new AtomicInteger();
		doAnswer(invocation -> {
			assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class))
					.isEqualTo("repeatable read");
			Object result = invocation.callRealMethod();
			if (reads.incrementAndGet() == 1) {
				otherTransaction.executeWithoutResult(transaction -> jdbc.update(
						"UPDATE team_requests SET status = 'CLOSED' WHERE id = ?", lastRequest));
			}
			return result;
		}).when(ranker).rank(any(), any());
		assertThat(create(UUID.randomUUID()).entryCount()).isEqualTo(501);
		assertThat(create(UUID.randomUUID()).entryCount()).isEqualTo(500);
	}

	@Test
	void impressionsReorderOnlyNewSessionsWhileDeliveryAndRetryPreserveOrder() {
		Seed fresh = team();
		Seed older = team();
		jdbc.update("UPDATE team_requests SET created_at = ? WHERE id = ?", now.minusDays(1), older.request());
		UUID key = UUID.randomUUID();
		var original = service.createForUser(viewer, key, 1);
		var first = pages.findForUser(viewer, original.sessionId(), null);
		assertThat(first.items().getFirst().team().id()).isEqualTo(fresh.team());
		assertThat(orderOf(create(UUID.randomUUID()))).containsExactly(fresh.team(), older.team());
		impressionService.recordForUser(viewer, original.sessionId(), List.of(first.items().getFirst().entryId()));
		var next = create(UUID.randomUUID());
		assertThat(orderOf(next)).containsExactly(older.team(), fresh.team());
		assertThat(service.createForUser(viewer, key, 1).sessionId()).isEqualTo(original.sessionId());
		assertThat(orderOf(original)).containsExactly(fresh.team(), older.team());
		var second = pages.findForUser(viewer, original.sessionId(), first.nextCursor());
		assertThat(pages.findForUser(viewer, original.sessionId(), second.previousCursor())).isEqualTo(first);
	}

	@Test
	void exactCooldownBoundaryUsesBaseRankingAndJustInsideMovesToFallback() {
		Seed fresh = team();
		Seed older = team();
		jdbc.update("UPDATE team_requests SET created_at = ? WHERE id = ?", now.minusDays(1), older.request());
		upsertImpression(jdbc, viewer, fresh.team(), now.minusDays(3));
		assertThat(orderOf(create(UUID.randomUUID()))).containsExactly(fresh.team(), older.team());
		upsertImpression(jdbc, viewer, fresh.team(), now.minusDays(3).plusNanos(1000));
		assertThat(orderOf(create(UUID.randomUUID()))).containsExactly(older.team(), fresh.team());
	}

	@Test
	void allRecentTeamsRemainAvailableOldestFirstAndOtherUsersHistoryIsIgnored() throws Exception {
		Seed fresh = team();
		Seed older = team();
		jdbc.update("UPDATE team_requests SET created_at = ? WHERE id = ?", now.minusDays(1), older.request());
		UUID other = UUID.fromString(AuthenticatedTestUser.register(mockMvc, objectMapper).userId());
		upsertImpression(jdbc, other, fresh.team(), now);
		assertThat(orderOf(create(UUID.randomUUID()))).containsExactly(fresh.team(), older.team());
		upsertImpression(jdbc, viewer, fresh.team(), now.minusHours(1));
		upsertImpression(jdbc, viewer, older.team(), now.minusDays(2));
		assertThat(orderOf(create(UUID.randomUUID()))).containsExactly(older.team(), fresh.team());
	}

	@Test
	void historyCannotReintroduceUnavailableTeams() {
		Seed hidden = team();
		Seed available = team();
		upsertImpression(jdbc, viewer, hidden.team(), now.minusDays(2));
		upsertImpression(jdbc, viewer, available.team(), now.minusHours(1));
		jdbc.update("UPDATE team_requests SET status = 'CLOSED' WHERE id = ?", hidden.request());
		assertThat(orderOf(create(UUID.randomUUID()))).containsExactly(available.team());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void waitingCreatorUsesCommittedImpressionOrIgnoresRolledBackOne(boolean rollback) throws Exception {
		Seed fresh = team();
		Seed older = team();
		jdbc.update("UPDATE team_requests SET created_at = ? WHERE id = ?", now.minusDays(1), older.request());
		var original = create(UUID.randomUUID());
		UUID entry = pages.findForUser(viewer, original.sessionId(), null).items().stream()
				.filter(item -> item.team().id().equals(fresh.team())).findFirst().orElseThrow().entryId();
		clearInvocations(writer);
		try (var executor = Executors.newSingleThreadExecutor()) {
			var pending = new AtomicReference<Future<TeamRecommendationSessionResult>>();
			Runnable transaction = () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				impressionService.recordForUser(viewer, original.sessionId(), List.of(entry));
				pending.set(executor.submit(() -> create(UUID.randomUUID())));
				awaitCreatorAccountLock();
				if (rollback) {
					throw new IllegalStateException("rollback impression");
				}
			});
			if (rollback) {
				assertThatThrownBy(transaction::run).isInstanceOf(IllegalStateException.class)
						.hasMessage("rollback impression");
			} else {
				transaction.run();
			}
			var created = pending.get().get(30, TimeUnit.SECONDS);
			assertThat(orderOf(created)).containsExactlyElementsOf(rollback
					? List.of(fresh.team(), older.team()) : List.of(older.team(), fresh.team()));
			verify(writer, times(rollback ? 1 : 2)).serializeCreation(viewer);
		}
	}

	private List<UUID> orderOf(TeamRecommendationSessionResult session) {
		return entries.findWindow(session.sessionId(), 0, session.entryCount()).stream()
				.map(TeamRecommendationEntry::getTeamId).toList();
	}

	private void awaitCreatorAccountLock() {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			jdbc.execute("SELECT pg_stat_clear_snapshot()");
			if (Boolean.TRUE.equals(jdbc.queryForObject("""
					SELECT EXISTS (SELECT 1 FROM pg_stat_activity
					WHERE datname = current_database() AND pid <> pg_backend_pid()
					  AND wait_event_type = 'Lock' AND LOWER(query) LIKE '%update users%')
					""", Boolean.class))) {
				return;
			}
			try {
				Thread.sleep(20);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
		}
		throw new AssertionError("Creator did not wait for the impression transaction");
	}

	private List<TeamRecommendationSessionResult> concurrent(UUID first, UUID second) throws Exception {
		try (var executor = Executors.newFixedThreadPool(2)) {
			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch start = new CountDownLatch(1);
			var tasks = new ArrayList<java.util.concurrent.Future<TeamRecommendationSessionResult>>();
			for (UUID key : List.of(first, second)) {
				tasks.add(executor.submit(() -> {
					ready.countDown();
					assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
					return create(key);
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return List.of(tasks.getFirst().get(30, TimeUnit.SECONDS), tasks.getLast().get(30, TimeUnit.SECONDS));
		}
	}

	private TeamRecommendationSessionResult create(UUID key) {
		return service.createForUser(viewer, key, 20);
	}

	private void setTime(LocalDateTime value) {
		ZoneId zone = ZoneId.systemDefault();
		when(clock.getZone()).thenReturn(zone);
		when(clock.instant()).thenReturn(value.atZone(zone).toInstant());
	}

	private long countEntries() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM team_recommendation_entries", Long.class);
	}

	private Seed team() {
		UUID team = UUID.randomUUID();
		UUID founder = UUID.randomUUID();
		UUID position = UUID.randomUUID();
		UUID request = UUID.randomUUID();
		jdbc.update("INSERT INTO teams (id, owner_profile_id, name, purpose) VALUES (?, ?, 'Team', 'Project')",
				team, ownerProfile);
		jdbc.update("""
				INSERT INTO team_positions (id, team_id, name, capacity)
				VALUES (?, ?, 'Founder', 1), (?, ?, 'Backend', 2)
				""", founder, team, position, team);
		jdbc.update("INSERT INTO team_members (team_id, profile_id, position_id) VALUES (?, ?, ?)",
				team, ownerProfile, founder);
		jdbc.update("""
				INSERT INTO team_requests (id, team_id, title, created_at, recruitment_deadline)
				VALUES (?, ?, 'Recruitment', ?, ?)
				""", request, team, now, now.plusDays(2));
		jdbc.update("""
				INSERT INTO team_request_positions (id, request_id, team_position_id) VALUES (?, ?, ?)
				""", UUID.randomUUID(), request, position);
		return new Seed(team, position, request);
	}

	private record Seed(UUID team, UUID position, UUID request) {
	}
}
