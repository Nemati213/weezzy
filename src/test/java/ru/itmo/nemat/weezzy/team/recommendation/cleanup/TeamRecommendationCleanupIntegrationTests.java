package ru.itmo.nemat.weezzy.team.recommendation.cleanup;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.team.recommendation.impression.TeamRecommendationImpressionRepository;
import ru.itmo.nemat.weezzy.team.recommendation.page.TeamRecommendationPageService;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionNotFoundException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static ru.itmo.nemat.weezzy.support.TeamRecommendationTestData.upsertImpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false", "app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false",
		"app.team-recommendation.cleanup.batch-size=2",
		"app.team-recommendation.cleanup.max-batches-per-run=2"
})
class TeamRecommendationCleanupIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));

	@Autowired private TeamRecommendationCleanupBatchService batches;
	@Autowired private TeamRecommendationCleanupService service;
	@Autowired private TeamRecommendationImpressionRepository history;
	@Autowired private TeamRecommendationPageService pages;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private PlatformTransactionManager transactions;
	@Autowired private MeterRegistry metrics;
	@MockitoSpyBean private TeamRecommendationCleanupRepository repository;
	@MockitoBean private Clock clock;
	private LocalDateTime now;
	private UUID user;
	private UUID ownerProfile;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void setUp() {
		now = LocalDateTime.now().withNano(0);
		setTime(now);
		jdbc.update("DELETE FROM team_recommendation_sessions");
		jdbc.update("DELETE FROM team_recommendation_impressions");
		user = UUID.randomUUID();
		ownerProfile = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, email, password_hash, role, created_at)
				VALUES (?, ?, 'unused', 'USER', ?)
				""", user, user + "@test.local", now);
		jdbc.update("""
				INSERT INTO profiles (id, display_name, status, created_at, updated_at)
				VALUES (?, 'Owner', 'ACTIVE', ?, ?)
				""", ownerProfile, now, now);
	}

	@Test
	void exactSessionExpiryDeletesButExactHistoryRetentionIsKept() {
		UUID expired = session(now, 1);
		UUID active = session(now.plusNanos(1000), 1);
		UUID old = team();
		UUID exact = team();
		UUID recent = team();
		upsertImpression(jdbc, user, old, now.minusDays(30).minusNanos(1000));
		upsertImpression(jdbc, user, exact, now.minusDays(30));
		upsertImpression(jdbc, user, recent, now.minusDays(1));
		assertThat(batch(10)).isEqualTo(new TeamRecommendationCleanupResult(1, 1, 1));
		assertThat(exists(expired)).isFalse();
		assertThat(exists(active)).isTrue();
		assertThat(history.findByOwnerUserIdAndTeamIdIn(user, List.of(old, exact, recent)))
				.extracting(impression -> impression.getTeamId()).containsExactlyInAnyOrder(exact, recent);
		assertThat(batch(10).isEmpty()).isTrue();
	}

	@Test
	void largeSessionIsRemovedOverBoundedBatchesWithoutCascadeBurst() {
		UUID expired = session(now.minusMinutes(1), 7);
		assertThat(batch(2)).isEqualTo(new TeamRecommendationCleanupResult(0, 2, 0));
		assertThat(entryCount(expired)).isEqualTo(5);
		assertThat(exists(expired)).isTrue();
		assertThatThrownBy(() -> pages.findForUser(user, expired, null))
				.isInstanceOf(TeamRecommendationSessionNotFoundException.class);
		assertThat(batch(2)).isEqualTo(new TeamRecommendationCleanupResult(0, 2, 0));
		assertThat(batch(2)).isEqualTo(new TeamRecommendationCleanupResult(0, 2, 0));
		assertThat(batch(2)).isEqualTo(new TeamRecommendationCleanupResult(1, 1, 0));
		assertThat(exists(expired)).isFalse();
	}

	@Test
	void runStopsAtConfiguredBudgetAndNextRunFinishesRemainingWork() {
		UUID expired = session(now.minusMinutes(1), 7);
		for (int i = 0; i < 7; i++) {
			upsertImpression(jdbc, user, team(), now.minusDays(31));
		}
		assertThat(service.cleanup()).isEqualTo(new TeamRecommendationCleanupResult(0, 4, 4));
		assertThat(entryCount(expired)).isEqualTo(3);
		assertThat(service.cleanup()).isEqualTo(new TeamRecommendationCleanupResult(1, 3, 3));
		assertThat(service.cleanup().isEmpty()).isTrue();
	}

	@Test
	void emptyExpiredSessionsAreLimitedAndActiveEntryDeduplicationSurvives() {
		for (int i = 0; i < 3; i++) {
			session(now.minusSeconds(1), 0);
		}
		UUID active = session(now.plusHours(1), 1);
		jdbc.update("""
				UPDATE team_recommendation_entries SET first_delivered_at = ?, first_shown_at = ?
				WHERE session_id = ?
				""", now.minusMinutes(1), now, active);
		assertThat(batch(2)).isEqualTo(new TeamRecommendationCleanupResult(2, 0, 0));
		assertThat(batch(2)).isEqualTo(new TeamRecommendationCleanupResult(1, 0, 0));
		assertThat(jdbc.queryForObject("""
				SELECT first_shown_at FROM team_recommendation_entries WHERE session_id = ?
				""", LocalDateTime.class, active)).isEqualTo(now);
	}

	@Test
	void deletingExpiredSessionDoesNotDeleteRecentHistory() {
		UUID expired = session(now, 1);
		UUID team = jdbc.queryForObject(
				"SELECT team_id FROM team_recommendation_entries WHERE session_id = ?", UUID.class, expired);
		upsertImpression(jdbc, user, team, now.minusDays(2));
		assertThat(batch(10)).isEqualTo(new TeamRecommendationCleanupResult(1, 1, 0));
		assertThat(history.findByOwnerUserIdAndTeamIdIn(user, List.of(team))).hasSize(1);
	}

	@Test
	void cleanupSkipsSessionHeldByPageReadEvenIfItExpiresDuringRead() throws Exception {
		UUID session = session(now.minusSeconds(1), 1);
		setTime(now.minusMinutes(1));
		var result = whileLocked(() -> {
			pages.findForUser(user, session, null);
			setTime(now);
		}, () -> batch(10));
		assertThat(result.isEmpty()).isTrue();
		assertThat(exists(session)).isTrue();
		assertThat(entryCount(session)).isEqualTo(1);
		assertThat(batch(10)).isEqualTo(new TeamRecommendationCleanupResult(1, 1, 0));
	}

	@Test
	void lockedChildDoesNotBlockOtherEntryDeletionOrTriggerCascade() throws Exception {
		UUID session = session(now, 2);
		UUID lockedEntry = jdbc.queryForObject("""
				SELECT id FROM team_recommendation_entries WHERE session_id = ? AND ordinal = 0
				""", UUID.class, session);
		var result = whileLocked(() -> jdbc.queryForObject(
				"SELECT id FROM team_recommendation_entries WHERE id = ? FOR UPDATE", UUID.class, lockedEntry),
				() -> batch(10));
		assertThat(result).isEqualTo(new TeamRecommendationCleanupResult(0, 1, 0));
		assertThat(exists(session)).isTrue();
		assertThat(batch(10)).isEqualTo(new TeamRecommendationCleanupResult(1, 1, 0));
	}

	@Test
	void lockedHistoryIsSkippedAndCommittedNewImpressionSurvives() throws Exception {
		UUID team = team();
		upsertImpression(jdbc, user, team, now.minusDays(31));
		var result = whileLocked(() -> upsertImpression(jdbc, user, team, now), () -> batch(10));
		assertThat(result.isEmpty()).isTrue();
		assertThat(batch(10).isEmpty()).isTrue();
		assertThat(history.findByOwnerUserIdAndTeamIdIn(user, List.of(team)).getFirst().getLastShownAt())
				.isEqualTo(now);
	}

	@Test
	void newImpressionCanRecreateHistoryAfterCleanupCommitted() {
		UUID team = team();
		upsertImpression(jdbc, user, team, now.minusDays(31));
		assertThat(batch(10)).isEqualTo(new TeamRecommendationCleanupResult(0, 0, 1));
		upsertImpression(jdbc, user, team, now);
		assertThat(batch(10).isEmpty()).isTrue();
		assertThat(history.findByOwnerUserIdAndTeamIdIn(user, List.of(team)).getFirst().getLastShownAt())
				.isEqualTo(now);
	}

	@Test
	void twoWorkersDoNotDeleteTheSameSessionOrHistoryTwice() throws Exception {
		for (int i = 0; i < 6; i++) {
			session(now, 0);
			upsertImpression(jdbc, user, team(), now.minusDays(31));
		}
		try (var executor = Executors.newFixedThreadPool(2)) {
			CountDownLatch start = new CountDownLatch(1);
			Callable<TeamRecommendationCleanupResult> work = () -> {
				assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
				return batch(3);
			};
			var first = executor.submit(work);
			var second = executor.submit(work);
			start.countDown();
			assertThat(first.get(10, TimeUnit.SECONDS).plus(second.get(10, TimeUnit.SECONDS)))
					.isEqualTo(new TeamRecommendationCleanupResult(6, 0, 6));
			assertThat(batch(10).isEmpty()).isTrue();
		}
	}

	@Test
	void secondWorkerProcessesOtherExpiredSessionsWhileFirstHoldsLocks() throws Exception {
		session(now, 1);
		session(now, 1);
		var result = whileLocked(() -> {
			var ids = repository.lockExpiredSessions(now, 1);
			assertThat(ids).hasSize(1);
		}, () -> batch(1));
		assertThat(result).isEqualTo(new TeamRecommendationCleanupResult(1, 1, 0));
		assertThat(batch(1)).isEqualTo(new TeamRecommendationCleanupResult(1, 1, 0));
	}

	@Test
	void laterBatchFailureRollsBackItsDeletesButKeepsPriorCommitAndMetrics() {
		session(now, 2);
		session(now, 2);
		for (int i = 0; i < 4; i++) {
			upsertImpression(jdbc, user, team(), now.minusDays(31));
		}
		double deletedBefore = metrics.get("weezzy.team.recommendation.cleanup.deleted")
				.tag("kind", "entry").counter().count();
		double failuresBefore = metrics.get("weezzy.team.recommendation.cleanup.runs")
				.tag("outcome", "failure").counter().count();
		AtomicInteger calls = new AtomicInteger();
		doAnswer(invocation -> {
			if (calls.incrementAndGet() == 2) {
				throw new TransientDataAccessResourceException("second batch failed");
			}
			return invocation.callRealMethod();
		}).when(AopTestUtils.<TeamRecommendationCleanupRepository>getUltimateTargetObject(repository))
				.deleteOldImpressions(any(), anyInt());
		assertThatThrownBy(service::cleanup).isInstanceOf(TransientDataAccessResourceException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team_recommendation_entries", Long.class))
				.isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team_recommendation_sessions", Long.class))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team_recommendation_impressions", Long.class))
				.isEqualTo(2);
		assertThat(metrics.get("weezzy.team.recommendation.cleanup.deleted")
				.tag("kind", "entry").counter().count()).isEqualTo(deletedBefore + 2);
		assertThat(metrics.get("weezzy.team.recommendation.cleanup.runs")
				.tag("outcome", "failure").counter().count()).isEqualTo(failuresBefore + 1);
	}

	private TeamRecommendationCleanupResult whileLocked(
			Runnable lock, Callable<TeamRecommendationCleanupResult> other
	) throws Exception {
		try (var executor = Executors.newSingleThreadExecutor()) {
			return new TransactionTemplate(transactions).execute(status -> {
				lock.run();
				try {
					return executor.submit(other).get(10, TimeUnit.SECONDS);
				} catch (Exception exception) {
					throw new IllegalStateException(exception);
				}
			});
		}
	}

	private TeamRecommendationCleanupResult batch(int size) {
		return batches.deleteBatch(now, now.minusDays(30), size);
	}

	private UUID session(LocalDateTime expires, int count) {
		UUID session = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO team_recommendation_sessions
				(id, owner_user_id, client_request_id, created_at, expires_at, page_size, entry_count)
				VALUES (?, ?, ?, ?, ?, 20, ?)
				""", session, user, UUID.randomUUID(), now.minusDays(2), expires, count);
		for (int i = 0; i < count; i++) {
			jdbc.update("""
					INSERT INTO team_recommendation_entries (id, session_id, ordinal, team_id)
					VALUES (?, ?, ?, ?)
					""", UUID.randomUUID(), session, i, team());
		}
		return session;
	}

	private UUID team() {
		UUID team = UUID.randomUUID();
		jdbc.update("INSERT INTO teams (id, owner_profile_id, name, purpose) VALUES (?, ?, 'Team', 'Project')",
				team, ownerProfile);
		return team;
	}

	private boolean exists(UUID session) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM team_recommendation_sessions WHERE id = ?)", Boolean.class, session));
	}

	private long entryCount(UUID session) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM team_recommendation_entries WHERE session_id = ?",
				Long.class, session);
	}

	private void setTime(LocalDateTime time) {
		when(clock.getZone()).thenReturn(ZoneId.systemDefault());
		when(clock.instant()).thenReturn(time.atZone(ZoneId.systemDefault()).toInstant());
	}
}
