package ru.itmo.nemat.weezzy.team.recommendation;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.data.domain.PageRequest;
import ru.itmo.nemat.weezzy.team.recommendation.candidate.TeamRecommendationCandidateRepository;
import ru.itmo.nemat.weezzy.team.recommendation.cleanup.TeamRecommendationCleanupBatchService;
import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationPageResponse;
import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationResponse;
import ru.itmo.nemat.weezzy.team.recommendation.impression.TeamRecommendationImpressionService;
import ru.itmo.nemat.weezzy.team.recommendation.page.TeamRecommendationPageService;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionService;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("volume")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false", "app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false"
})
class TeamRecommendationVolumeTests {
	private static final int OPEN_TEAMS = 3000;
	private static final int CLOSED_TEAMS = 1000;
	private static final int TEAMS = OPEN_TEAMS + CLOSED_TEAMS;
	private static final int MEMBERS = 2000;
	private static final int HISTORY_USERS = 2000;
	private static final int HISTORY_PER_USER = 500;
	private static final int EXPIRED_SESSIONS = 200;
	private static final List<String> CATALOG = List.of("teams", "team_requests",
			"team_request_positions", "team_positions", "team_members", "team_position_skills",
			"team_applications", "profiles", "users", "account_sanctions", "profile_blocks",
			"team_recommendation_sessions", "team_recommendation_entries",
			"team_recommendation_impressions");
	private static final List<String> LARGE = List.of(
			"team_recommendation_entries", "team_recommendation_impressions");
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));

	static {
		postgres.start();
	}

	@Autowired private JdbcTemplate jdbc;
	@Autowired private DataSource dataSource;
	@Autowired private EntityManagerFactory entityManagerFactory;
	@Autowired private TeamRecommendationSessionService sessions;
	@Autowired private TeamRecommendationPageService pages;
	@Autowired private TeamRecommendationImpressionService impressions;
	@Autowired private TeamRecommendationCleanupBatchService cleanup;
	@Autowired private TeamRecommendationCandidateRepository candidateRepository;
	private final Map<String, String> report = new LinkedHashMap<>();
	private UUID viewer;
	private UUID sessionId;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeAll
	void seed() {
		var pool = (HikariDataSource) dataSource;
		pool.getHikariConfigMXBean().setMinimumIdle(1);
		pool.getHikariConfigMXBean().setMaximumPoolSize(1);
		pool.getHikariPoolMXBean().softEvictConnections();
		long started = System.nanoTime();
		jdbc.update("""
				INSERT INTO users (id, email, password_hash, role, created_at, email_verified_at)
				SELECT md5('user' || n)::uuid, 'volume-' || n || '@itmo.ru', 'x', 'USER',
				       now() - interval '60 days', now() - interval '60 days'
				FROM generate_series(1, ?) n
				""", TEAMS + MEMBERS + HISTORY_USERS + 1);
		jdbc.update("""
				INSERT INTO profiles (id, display_name, created_at, updated_at, status, user_id)
				SELECT md5('profile' || n)::uuid, 'Volume ' || n, now(), now(), 'ACTIVE',
				       md5('user' || n)::uuid
				FROM generate_series(1, ?) n
				""", TEAMS + MEMBERS + HISTORY_USERS + 1);
		jdbc.update("""
				INSERT INTO skills (id, name, created_at)
				SELECT md5('skill' || k)::uuid, 'Volume skill ' || k, now()
				FROM generate_series(1, 300) k
				""");
		jdbc.update("""
				INSERT INTO teams (id, owner_profile_id, name, purpose, status, created_at, updated_at)
				SELECT md5('team' || n)::uuid, md5('profile' || n)::uuid, 'Team ' || n,
				       'Purpose ' || n, 'ACTIVE', now() - n * interval '1 minute', now()
				FROM generate_series(1, ?) n
				""", TEAMS);
		jdbc.update("""
				INSERT INTO team_positions (id, team_id, name, capacity, created_at, updated_at)
				SELECT md5(kind || n)::uuid, md5('team' || n)::uuid, kind || ' ' || n,
				       CASE kind WHEN 'founder' THEN 1 ELSE 3 END,
				       now() - n * interval '1 second', now()
				FROM generate_series(1, ?) n CROSS JOIN (VALUES ('founder'), ('pos-a'), ('pos-b')) k(kind)
				""", TEAMS);
		jdbc.update("""
				INSERT INTO team_members (team_id, profile_id, position_id, joined_at)
				SELECT md5('team' || n)::uuid, md5('profile' || n)::uuid, md5('founder' || n)::uuid, now()
				FROM generate_series(1, ?) n
				""", TEAMS);
		jdbc.update("""
				INSERT INTO team_members (team_id, profile_id, position_id, joined_at)
				SELECT md5('team' || n)::uuid, md5('profile' || (? + n))::uuid,
				       md5('pos-a' || n)::uuid, now()
				FROM generate_series(1, ?) n
				""", TEAMS, MEMBERS);
		jdbc.update("""
				INSERT INTO team_position_skills (position_id, skill_id)
				SELECT DISTINCT md5(kind || n)::uuid, md5('skill' || ((n * 7 + j * 13) % 300 + 1))::uuid
				FROM generate_series(1, ?) n CROSS JOIN (VALUES ('pos-a'), ('pos-b')) k(kind)
				CROSS JOIN generate_series(1, 3) j
				""", TEAMS);
		jdbc.update("""
				INSERT INTO team_requests (id, team_id, title, status, recruitment_deadline,
				                           created_at, updated_at)
				SELECT md5('req' || n)::uuid, md5('team' || n)::uuid, 'Recruitment ' || n,
				       CASE WHEN n <= ? THEN 'OPEN' ELSE 'CLOSED' END, now() + interval '7 days',
				       now() - n * interval '1 minute', now()
				FROM generate_series(1, ?) n
				""", OPEN_TEAMS, TEAMS);
		jdbc.update("""
				INSERT INTO team_request_positions (id, request_id, team_position_id, created_at)
				SELECT md5('link-' || kind || n)::uuid, md5('req' || n)::uuid, md5(kind || n)::uuid, now()
				FROM generate_series(1, ?) n CROSS JOIN (VALUES ('pos-a'), ('pos-b')) k(kind)
				""", TEAMS);
		viewer = uuid("user", TEAMS + MEMBERS + HISTORY_USERS + 1);
		jdbc.update("""
				INSERT INTO profile_skills (profile_id, skill_id, created_at)
				SELECT md5('profile' || ?)::uuid, md5('skill' || k)::uuid, now()
				FROM generate_series(1, 10) k
				""", TEAMS + MEMBERS + HISTORY_USERS + 1);
		jdbc.update("""
				INSERT INTO team_recommendation_impressions (owner_user_id, team_id, last_shown_at)
				SELECT md5('user' || (? + u))::uuid, md5('team' || ((u * 37 + t) % ? + 1))::uuid,
				       now() - (t % 40) * interval '1 day' - u * interval '1 second'
				FROM generate_series(1, ?) u CROSS JOIN generate_series(1, ?) t
				""", TEAMS + MEMBERS, TEAMS, HISTORY_USERS, HISTORY_PER_USER);
		jdbc.update("""
				INSERT INTO team_recommendation_impressions (owner_user_id, team_id, last_shown_at)
				SELECT ?, md5('team' || n)::uuid, now() - interval '1 hour'
				FROM generate_series(1, 1500) n
				""", viewer);
		jdbc.update("""
				INSERT INTO team_recommendation_sessions
				(id, owner_user_id, client_request_id, created_at, expires_at, page_size, entry_count)
				SELECT md5('expired' || s)::uuid, md5('user' || (? + s))::uuid, gen_random_uuid(),
				       now() - interval '3 days', now() - interval '2 days', 20, ?
				FROM generate_series(1, ?) s
				""", TEAMS + MEMBERS, OPEN_TEAMS, EXPIRED_SESSIONS);
		jdbc.update("""
				INSERT INTO team_recommendation_entries (id, session_id, ordinal, team_id)
				SELECT gen_random_uuid(), md5('expired' || s)::uuid, o, md5('team' || (o + 1))::uuid
				FROM generate_series(1, ?) s CROSS JOIN generate_series(0, ? - 1) o
				""", EXPIRED_SESSIONS, OPEN_TEAMS);
		jdbc.execute("ANALYZE");
		record("seed", millis(started) + " ms");
	}

	@Test
	@Order(1)
	void fullSnapshotCreationIsBoundedAndUsesBatches() {
		long created = time("create session (" + OPEN_TEAMS + " teams)", () -> sessions
				.createForUser(viewer, UUID.randomUUID(), 20).sessionId(), id -> sessionId = id);
		assertThat(jdbc.queryForObject("""
				SELECT entry_count FROM team_recommendation_sessions WHERE id = ?
				""", Integer.class, sessionId)).isEqualTo(OPEN_TEAMS);
		assertThat(jdbc.queryForObject("""
				SELECT team_id FROM team_recommendation_entries WHERE session_id = ? AND ordinal = 0
				""", UUID.class, sessionId)).isNotIn(recentlyShown());
		record("entry bytes (avg, incl. indexes)", String.valueOf(jdbc.queryForObject("""
				SELECT pg_total_relation_size('team_recommendation_entries')
				       / GREATEST(1, (SELECT COUNT(*) FROM team_recommendation_entries))
				""", Long.class)));
		assertThat(created).isLessThan(10_000);
		UUID profile = jdbc.queryForObject("SELECT id FROM profiles WHERE user_id = ?", UUID.class, viewer);
		LocalDateTime now = LocalDateTime.now();
		for (int size : List.of(500, 2000)) {
			candidateRepository.findEligible(profile, now, now.minusDays(1), null, PageRequest.of(0, size));
			long started = System.nanoTime();
			int rows = candidateRepository.findEligible(profile, now, now.minusDays(1), null,
					PageRequest.of(0, size)).size();
			record("candidate batch " + size, millis(started) + " ms, rows=" + rows);
		}
	}

	@Test
	@Order(2)
	void pageReadDoesNotScanTheCatalogAndHasConstantQueryCount() {
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		try {
			TeamRecommendationPageResponse page = null;
			String cursor = null;
			long warm = 0;
			long queries = -1;
			long catalogRows = 0;
			for (int i = 0; i < 5; i++) {
				String current = cursor;
				var before = scans();
				statistics.clear();
				long started = System.nanoTime();
				page = pages.findForUser(viewer, sessionId, current);
				long elapsed = millis(started);
				if (i > 0) {
					warm = Math.max(warm, elapsed);
				}
				long count = statistics.getPrepareStatementCount();
				assertThat(queries < 0 || count == queries).isTrue();
				queries = count;
				var scanned = delta(before, scans());
				record("page " + i, elapsed + " ms, seq scans " + scanned);
				assertThat(scanned).doesNotContainKeys(LARGE.toArray(String[]::new));
				scanned.forEach((table, rows) -> assertThat(rows)
						.as("at most one pass over " + table).isLessThanOrEqualTo(rowCount(table)));
				catalogRows = Math.max(catalogRows,
						scanned.values().stream().mapToLong(Long::longValue).sum());
				assertThat(page.items()).hasSize(20);
				cursor = page.nextCursor();
			}
			record("page read SQL statements", String.valueOf(queries));
			record("page read catalog rows by seq scan (max)", String.valueOf(catalogRows));
			assertThat(queries).isLessThanOrEqualTo(20);
			assertThat(warm).isLessThan(500);
			var entries = page.items().stream().map(TeamRecommendationResponse::entryId).toList();
			var before = scans();
			time("impressions x20", () -> {
				impressions.recordForUser(viewer, sessionId, entries);
				return null;
			}, ignored -> { });
			var scanned = delta(before, scans());
			record("impressions seq scans", scanned.toString());
			assertThat(scanned).doesNotContainKeys(LARGE.toArray(String[]::new));
		} finally {
			statistics.setStatisticsEnabled(false);
		}
	}

	@Test
	@Order(3)
	void evictionOfFullSnapshotsStaysCheap() {
		long total = 0;
		for (int i = 0; i < 9; i++) {
			long started = System.nanoTime();
			sessions.createForUser(viewer, UUID.randomUUID(), 20);
			total += millis(started);
		}
		record("create session warm avg (no eviction)", total / 9 + " ms");
		var before = scans();
		long withEviction = time("create session with eviction", () -> sessions
				.createForUser(viewer, UUID.randomUUID(), 20).sessionId(), ignored -> { });
		var scanned = delta(before, scans());
		record("create session seq scans", scanned.toString());
		assertThat(scanned).doesNotContainKeys(LARGE.toArray(String[]::new));
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM team_recommendation_sessions WHERE id = ?
				""", Long.class, sessionId)).isZero();
		assertThat(withEviction).isLessThan(10_000);
	}

	@Test
	@Order(4)
	void cleanupThroughputCoversRealisticCreationRate() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime cutoff = now.minusDays(30);
		for (int size : List.of(500, 5000, 20000)) {
			var before = scans();
			long started = System.nanoTime();
			var result = cleanup.deleteBatch(now, cutoff, size);
			long elapsed = Math.max(1, millis(started));
			var scanned = delta(before, scans());
			record("cleanup batch " + size, elapsed + " ms, entries=" + result.entries()
					+ ", sessions=" + result.sessions() + ", history=" + result.impressions()
					+ ", seq scans " + scanned);
			assertThat(result.entries()).isEqualTo(size);
			assertThat(scanned).doesNotContainKeys(LARGE.toArray(String[]::new));
		}
		System.out.println("VOLUME REPORT " + report);
	}

	private List<UUID> recentlyShown() {
		return jdbc.queryForList("""
				SELECT team_id FROM team_recommendation_impressions WHERE owner_user_id = ?
				""", UUID.class, viewer);
	}

	private long catalogScanRows() {
		return scans().values().stream().mapToLong(Long::longValue).sum();
	}

	private Map<String, Long> scans() {
		jdbc.queryForObject("SELECT pg_stat_force_next_flush()", Object.class);
		jdbc.queryForObject("SELECT pg_stat_clear_snapshot()", Object.class);
		Map<String, Long> result = new LinkedHashMap<>();
		jdbc.query("""
				SELECT relname, COALESCE(seq_tup_read, 0) FROM pg_stat_user_tables
				WHERE relname = ANY (?) ORDER BY relname
				""", row -> {
			result.put(row.getString(1), row.getLong(2));
		}, (Object) CATALOG.toArray(String[]::new));
		return result;
	}

	private long rowCount(String table) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
	}

	private Map<String, Long> delta(Map<String, Long> before, Map<String, Long> after) {
		Map<String, Long> result = new LinkedHashMap<>();
		after.forEach((table, rows) -> {
			long diff = rows - before.getOrDefault(table, 0L);
			if (diff > 0) {
				result.put(table, diff);
			}
		});
		return result;
	}

	private <T> long time(String name, Supplier<T> action, java.util.function.Consumer<T> result) {
		long started = System.nanoTime();
		T value = action.get();
		long elapsed = millis(started);
		result.accept(value);
		record(name, elapsed + " ms");
		return elapsed;
	}

	private void record(String name, String value) {
		report.put(name, value);
		System.out.println("VOLUME " + name + ": " + value);
	}

	private static long millis(long started) {
		return (System.nanoTime() - started) / 1_000_000;
	}

	private UUID uuid(String prefix, int n) {
		return jdbc.queryForObject("SELECT md5(? || ?)::uuid", UUID.class, prefix, String.valueOf(n));
	}
}
