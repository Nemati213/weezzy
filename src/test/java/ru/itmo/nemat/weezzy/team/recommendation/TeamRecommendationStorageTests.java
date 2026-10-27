package ru.itmo.nemat.weezzy.team.recommendation;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import ru.itmo.nemat.weezzy.team.recommendation.impression.TeamRecommendationImpression;
import ru.itmo.nemat.weezzy.team.recommendation.impression.TeamRecommendationImpressionRepository;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntry;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntryRepository;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSession;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static ru.itmo.nemat.weezzy.support.TeamRecommendationTestData.upsertImpression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false", "app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false"
})
class TeamRecommendationStorageTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));

	@Autowired
	private TeamRecommendationSessionRepository sessions;
	@Autowired
	private TeamRecommendationEntryRepository entries;
	@Autowired
	private TeamRecommendationImpressionRepository impressions;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private final LocalDateTime now = LocalDateTime.of(2026, 10, 28, 12, 0);
	private UUID owner;
	private UUID team;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void setUp() {
		owner = user();
		team = team();
	}

	@Test
	void roundTripsSessionAndEntriesThroughJpaWithoutLoadingTeams() {
		var session = session(owner, UUID.randomUUID(), now, 4);
		UUID secondTeam = team();
		var second = entry(session.getId(), 1, secondTeam);
		var first = entry(session.getId(), 0, team);
		entry(session.getId(), 2, team());
		entry(session.getId(), 3, team());
		entry(session(user(), UUID.randomUUID(), now, 1).getId(), 0, team);

		assertThat(sessions.findByOwnerUserIdAndClientRequestId(owner, session.getClientRequestId()))
				.get().extracting(TeamRecommendationSession::getId).isEqualTo(session.getId());
		assertThat(entries.findWindow(session.getId(), 0, 2))
				.extracting(TeamRecommendationEntry::getId).containsExactly(first.getId(), second.getId());
		assertThat(entries.findWindow(session.getId(), 1, 2)).singleElement()
				.extracting(TeamRecommendationEntry::getTeamId).isEqualTo(secondTeam);
		assertThat(entries.findWindow(session.getId(), 4, 6)).isEmpty();
		assertThat(first.getFirstDeliveredAt()).isNull();
		assertThat(first.getFirstShownAt()).isNull();
		first.setFirstDeliveredAt(now);
		first.setFirstShownAt(now.plusSeconds(1));
		entries.saveAndFlush(first);
		assertThat(entries.findById(first.getId())).get()
				.extracting(TeamRecommendationEntry::getFirstShownAt).isEqualTo(now.plusSeconds(1));
	}

	@Test
	void activeSessionsExcludeExpiredAndForeignWithStableEvictionOrder() {
		var first = session(owner, UUID.randomUUID(), now, 0);
		var second = session(owner, UUID.randomUUID(), now, 0);
		var older = session(owner, UUID.randomUUID(), now.minusHours(1), 0);
		session(owner, UUID.randomUUID(), now.minusDays(1), 0);
		session(user(), UUID.randomUUID(), now, 0);
		List<UUID> tied = List.of(first.getId(), second.getId()).stream()
				.sorted(Comparator.comparing(UUID::toString)).toList();
		assertThat(sessions.findActiveByOwner(owner, now)).extracting(TeamRecommendationSession::getId)
				.containsExactly(older.getId(), tied.getFirst(), tied.getLast());
	}

	@Test
	void requestKeyIsUniqueWithinOwnerButNotAcrossAccounts() {
		UUID key = UUID.randomUUID();
		session(owner, key, now, 0);
		assertThatThrownBy(() -> session(owner, key, now, 0))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(session(user(), key, now, 0).getId()).isNotNull();
	}

	@Test
	void uniqueOrdinalAndTeamAreScopedToSession() {
		var first = session(owner, UUID.randomUUID(), now, 2);
		entry(first.getId(), 0, team);
		assertThatThrownBy(() -> entry(first.getId(), 1, team))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> entry(first.getId(), 0, team()))
				.isInstanceOf(DataIntegrityViolationException.class);
		var second = session(owner, UUID.randomUUID(), now, 1);
		assertThat(entry(second.getId(), 0, team).getId()).isNotNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"page-zero", "page-large", "count-negative", "expiry-equal",
			"expiry-earlier", "missing-owner"})
	void rejectsInvalidSessions(String invalid) {
		var session = newSession(owner, UUID.randomUUID(), now, 0);
		switch (invalid) {
			case "page-zero" -> session.setPageSize(0);
			case "page-large" -> session.setPageSize(51);
			case "count-negative" -> session.setEntryCount(-1);
			case "expiry-equal" -> session.setExpiresAt(now);
			case "expiry-earlier" -> session.setExpiresAt(now.minusSeconds(1));
			case "missing-owner" -> session.setOwnerUserId(UUID.randomUUID());
			default -> throw new IllegalArgumentException(invalid);
		}
		assertThatThrownBy(() -> sessions.saveAndFlush(session))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"negative-ordinal", "shown-before-delivery", "shown-without-delivery",
			"missing-session", "missing-team"})
	void rejectsInvalidEntries(String invalid) {
		var session = session(owner, UUID.randomUUID(), now, 1);
		var entry = new TeamRecommendationEntry();
		entry.setSessionId(session.getId());
		entry.setTeamId(team);
		switch (invalid) {
			case "negative-ordinal" -> entry.setOrdinal(-1);
			case "shown-before-delivery" -> {
				entry.setFirstDeliveredAt(now);
				entry.setFirstShownAt(now.minusSeconds(1));
			}
			case "shown-without-delivery" -> entry.setFirstShownAt(now);
			case "missing-session" -> entry.setSessionId(UUID.randomUUID());
			case "missing-team" -> entry.setTeamId(UUID.randomUUID());
			default -> throw new IllegalArgumentException(invalid);
		}
		assertThatThrownBy(() -> entries.saveAndFlush(entry))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void impressionsAreUniqueMonotonicAndScopedByOwner() {
		UUID otherTeam = team();
		UUID otherOwner = user();
		upsertImpression(jdbc, owner, team, now);
		upsertImpression(jdbc, owner, team, now.minusDays(1));
		upsertImpression(jdbc, owner, team, now);
		upsertImpression(jdbc, owner, otherTeam, now.minusDays(3));
		upsertImpression(jdbc, otherOwner, team, now.plusHours(1));
		assertThat(impressions.findByOwnerUserIdAndTeamIdIn(owner, List.of(team))).singleElement()
				.extracting(TeamRecommendationImpression::getLastShownAt).isEqualTo(now);
		upsertImpression(jdbc, owner, team, now.plusSeconds(1));
		assertThat(impressions.findByOwnerUserIdAndTeamIdIn(owner, List.of(team, otherTeam)))
				.extracting(TeamRecommendationImpression::getLastShownAt)
				.containsExactlyInAnyOrder(now.plusSeconds(1), now.minusDays(3));
		assertThat(impressions.findByOwnerUserIdAndTeamIdIn(owner, List.of())).isEmpty();
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO team_recommendation_impressions VALUES (?, ?, ?)
				""", owner, team, now)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void impressionsRequireExistingAccountAndTeam() {
		assertThatThrownBy(() -> upsertImpression(jdbc, UUID.randomUUID(), team, now))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> upsertImpression(jdbc, owner, UUID.randomUUID(), now))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingSessionRemovesEntriesButPreservesCooldownHistory() {
		var session = session(owner, UUID.randomUUID(), now, 1);
		var entry = entry(session.getId(), 0, team);
		upsertImpression(jdbc, owner, team, now);
		sessions.deleteById(session.getId());
		assertThat(entries.findById(entry.getId())).isEmpty();
		assertThat(impressions.findByOwnerUserIdAndTeamIdIn(owner, List.of(team))).hasSize(1);
	}

	@Test
	void deletingAccountCascadesOnlyItsSessionsEntriesAndHistory() {
		var ownSession = session(owner, UUID.randomUUID(), now, 1);
		var ownEntry = entry(ownSession.getId(), 0, team);
		UUID otherOwner = user();
		var otherSession = session(otherOwner, UUID.randomUUID(), now, 1);
		var otherEntry = entry(otherSession.getId(), 0, team);
		upsertImpression(jdbc, owner, team, now);
		upsertImpression(jdbc, otherOwner, team, now);
		jdbc.update("DELETE FROM users WHERE id = ?", owner);
		assertThat(sessions.findById(ownSession.getId())).isEmpty();
		assertThat(entries.findById(ownEntry.getId())).isEmpty();
		assertThat(impressions.findByOwnerUserIdAndTeamIdIn(owner, List.of(team))).isEmpty();
		assertThat(sessions.findById(otherSession.getId())).isPresent();
		assertThat(entries.findById(otherEntry.getId())).isPresent();
		assertThat(impressions.findByOwnerUserIdAndTeamIdIn(otherOwner, List.of(team))).hasSize(1);
	}

	@Test
	void deletingTeamsLeavesMultipleEmptyOrdinalsAndOriginalCount() {
		var session = session(owner, UUID.randomUUID(), now, 3);
		UUID otherTeam = team();
		UUID retainedTeam = team();
		var first = entry(session.getId(), 0, team);
		var second = entry(session.getId(), 1, otherTeam);
		entry(session.getId(), 2, retainedTeam);
		upsertImpression(jdbc, owner, team, now);
		upsertImpression(jdbc, owner, otherTeam, now);
		upsertImpression(jdbc, owner, retainedTeam, now);
		jdbc.update("DELETE FROM teams WHERE id IN (?, ?)", team, otherTeam);
		assertThat(entries.findWindow(session.getId(), 0, 2))
				.extracting(TeamRecommendationEntry::getId).containsExactly(first.getId(), second.getId());
		assertThat(entries.findWindow(session.getId(), 0, 3))
				.extracting(TeamRecommendationEntry::getTeamId).containsExactly(null, null, retainedTeam);
		assertThat(sessions.findById(session.getId())).get()
				.extracting(TeamRecommendationSession::getEntryCount).isEqualTo(3);
		assertThat(impressions.findByOwnerUserIdAndTeamIdIn(owner, List.of(team, otherTeam, retainedTeam)))
				.extracting(TeamRecommendationImpression::getTeamId).containsExactly(retainedTeam);
	}

	@Test
	void windowAndHistoryReadsUseTwoQueriesRegardlessOfNumberOfEntries() {
		var session = session(owner, UUID.randomUUID(), now, 20);
		List<UUID> teamIds = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			UUID id = team();
			teamIds.add(id);
			entry(session.getId(), i, id);
			upsertImpression(jdbc, owner, id, now);
		}
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		try {
			for (int size : List.of(1, 20)) {
				statistics.clear();
				assertThat(entries.findWindow(session.getId(), 0, size))
						.extracting(TeamRecommendationEntry::getTeamId)
						.containsExactlyElementsOf(teamIds.subList(0, size));
				assertThat(impressions.findByOwnerUserIdAndTeamIdIn(owner, teamIds.subList(0, size)))
						.extracting(TeamRecommendationImpression::getTeamId)
						.containsExactlyInAnyOrderElementsOf(teamIds.subList(0, size));
				assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
			}
		} finally {
			statistics.setStatisticsEnabled(false);
		}
	}

	private TeamRecommendationSession session(UUID user, UUID requestId, LocalDateTime created, int count) {
		return sessions.saveAndFlush(newSession(user, requestId, created, count));
	}

	private TeamRecommendationSession newSession(UUID user, UUID requestId, LocalDateTime created, int count) {
		var session = new TeamRecommendationSession();
		session.setOwnerUserId(user);
		session.setClientRequestId(requestId);
		session.setCreatedAt(created);
		session.setExpiresAt(created.plusDays(1));
		session.setPageSize(20);
		session.setEntryCount(count);
		return session;
	}

	private TeamRecommendationEntry entry(UUID sessionId, int ordinal, UUID teamId) {
		var entry = new TeamRecommendationEntry();
		entry.setSessionId(sessionId);
		entry.setOrdinal(ordinal);
		entry.setTeamId(teamId);
		return entries.saveAndFlush(entry);
	}

	private UUID user() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, email, password_hash, role, created_at)
				VALUES (?, ?, 'unused', 'USER', ?)
				""", id, id + "@test.local", now);
		return id;
	}

	private UUID team() {
		UUID profileId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO profiles (id, display_name, status, created_at, updated_at)
				VALUES (?, 'Owner', 'ACTIVE', ?, ?)
				""", profileId, now, now);
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO teams (id, owner_profile_id, name, purpose)
				VALUES (?, ?, 'Team', 'Project')
				""", id, profileId);
		return id;
	}
}
