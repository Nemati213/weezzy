package ru.itmo.nemat.weezzy.team.recommendation.impression;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanction;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionRepository;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntry;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntryRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false", "app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false"
})
class TeamRecommendationImpressionIntegrationTests {
	private static final String BASE = "/api/team-recommendations/me/sessions/";
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper mapper;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private TeamRecommendationImpressionService service;
	@Autowired private TeamRecommendationEntryRepository entries;
	@Autowired private TeamRecommendationImpressionRepository history;
	@Autowired private AccountSanctionRepository sanctions;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private EntityManagerFactory entityManagerFactory;
	@MockitoSpyBean private TeamRecommendationImpressionWriteRepository writer;
	@MockitoBean private Clock clock;
	private AuthenticatedTestUser viewer;
	private UUID userId;
	private UUID profileId;
	private UUID ownerProfileId;
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
		viewer = AuthenticatedTestUser.register(mockMvc, mapper);
		userId = UUID.fromString(viewer.userId());
		profileId = UUID.fromString(viewer.createProfile("Viewer").id());
		ownerProfileId = UUID.fromString(AuthenticatedTestUser.register(mockMvc, mapper)
				.createProfile("Owner").id());
		jdbc.update("UPDATE profiles SET status = 'ACTIVE' WHERE id IN (?, ?)",
				profileId, ownerProfileId);
	}

	@Test
	void deliveryIsNotAnImpressionAndRepeatDoesNotExtendHistory() throws Exception {
		UUID team = team();
		UUID session = session(List.of(team), false);
		UUID entry = entryIds(session).getFirst();
		mockMvc.perform(viewer.authorize(get(BASE + session))).andExpect(status().isOk());
		assertThat(shown(entry)).isNull();
		assertThat(lastShown(team)).isNull();
		setTime(now.plusSeconds(2));
		send(session, List.of(entry)).andExpect(status().isNoContent());
		assertThat(shown(entry)).isEqualTo(now.plusSeconds(2));
		assertThat(lastShown(team)).isEqualTo(now.plusSeconds(2));
		setTime(now.plusHours(1));
		mockMvc.perform(viewer.authorize(get(BASE + session))).andExpect(status().isOk());
		send(session, List.of(entry)).andExpect(status().isNoContent());
		assertThat(shown(entry)).isEqualTo(now.plusSeconds(2));
		assertThat(lastShown(team)).isEqualTo(now.plusSeconds(2));
	}

	@Test
	void batchMaySpanPagesAndAnotherSessionCanAdvanceHistory() throws Exception {
		UUID first = team();
		UUID second = team();
		UUID session = session(List.of(first, second), true);
		send(session, entryIds(session)).andExpect(status().isNoContent());
		assertThat(lastShown(first)).isEqualTo(now);
		assertThat(lastShown(second)).isEqualTo(now);
		UUID another = session(List.of(first), true);
		setTime(now.plusHours(2));
		send(another, entryIds(another)).andExpect(status().isNoContent());
		assertThat(lastShown(first)).isEqualTo(now.plusHours(2));
		assertThat(lastShown(second)).isEqualTo(now);
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing", "foreign-entry", "undelivered"})
	void invalidEntryRollsBackWholeBatch(String reason) throws Exception {
		UUID first = team();
		UUID second = team();
		UUID session = session(List.of(first, second), true);
		var ids = new ArrayList<>(entryIds(session));
		switch (reason) {
			case "missing" -> ids.set(1, UUID.randomUUID());
			case "foreign-entry" -> ids.set(1, entryIds(session(List.of(team()), true)).getFirst());
			case "undelivered" -> jdbc.update(
					"UPDATE team_recommendation_entries SET first_delivered_at = NULL WHERE id = ?", ids.get(1));
		}
		send(session, ids).andExpect(status().isNotFound());
		assertThat(lastShown(first)).isNull();
		assertThat(lastShown(second)).isNull();
		assertThat(shown(ids.getFirst())).isNull();
	}

	@Test
	void duplicateIdsAndOversizedBatchAreBadRequests() throws Exception {
		UUID session = session(List.of(team()), true);
		UUID entry = entryIds(session).getFirst();
		send(session, List.of(entry, entry)).andExpect(status().isBadRequest());
		send(session, IntStream.range(0, 51).mapToObj(i -> UUID.randomUUID()).toList())
				.andExpect(status().isBadRequest());
		assertThat(shown(entry)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"entryIds\":null}", "{\"entryIds\":[]}",
			"{\"entryIds\":[null]}", "{\"entryIds\":[\"bad-uuid\"]}", "null"})
	void rejectsInvalidBody(String body) throws Exception {
		mockMvc.perform(viewer.authorize(post(BASE + UUID.randomUUID() + "/impressions"))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
	}

	@Test
	void requiresJwtAndHidesForeignUnknownAndExpiredSessions() throws Exception {
		UUID session = session(List.of(team()), true);
		var ids = entryIds(session);
		String body = mapper.writeValueAsString(Map.of("entryIds", ids));
		mockMvc.perform(post(BASE + session + "/impressions")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isUnauthorized());
		var other = AuthenticatedTestUser.register(mockMvc, mapper);
		mockMvc.perform(other.authorize(post(BASE + session + "/impressions"))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isNotFound());
		send(UUID.randomUUID(), ids).andExpect(status().isNotFound());
		setTime(now.plusDays(1));
		send(session, ids).andExpect(status().isNotFound());
		assertThat(shown(ids.getFirst())).isNull();
	}

	@Test
	void sanctionedViewerCannotRecordImpressions() throws Exception {
		UUID session = session(List.of(team()), true);
		var sanction = new AccountSanction();
		sanction.setTargetUserId(userId);
		sanction.setType(AccountSanctionType.PERMANENT_BAN);
		sanction.setReason("Test");
		sanction.setCreatedByUserId(userId);
		sanctions.saveAndFlush(sanction);
		send(session, entryIds(session)).andExpect(status().isForbidden());
		assertThat(shown(entryIds(session).getFirst())).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"closed", "expired", "full", "pending", "declined", "deleted", "blocked"})
	void delayedUnavailableTeamIsSkippedWhileValidTeamIsRecorded(String reason) throws Exception {
		UUID hidden = team();
		UUID available = team();
		UUID session = session(List.of(hidden, available), true);
		var ids = entryIds(session);
		switch (reason) {
			case "closed" -> jdbc.update("UPDATE team_requests SET status = 'CLOSED' WHERE team_id = ?", hidden);
			case "expired" -> jdbc.update("UPDATE team_requests SET recruitment_deadline = ? WHERE team_id = ?",
					now, hidden);
			case "full" -> jdbc.update("""
					INSERT INTO team_members (team_id, profile_id, position_id)
					SELECT ?, ?, id FROM team_positions WHERE team_id = ? AND name = 'Backend'
					""", hidden, profileId, hidden);
			case "pending", "declined" -> jdbc.update("""
					INSERT INTO team_applications (id, position_id, applicant_profile_id, status, resolved_at)
					SELECT ?, link.id, ?, ?, ? FROM team_request_positions link
					JOIN team_requests request ON request.id = link.request_id WHERE request.team_id = ?
					""", UUID.randomUUID(), profileId, reason.equals("pending") ? "PENDING" : "DECLINED",
					reason.equals("pending") ? null : now.minusMinutes(1), hidden);
			case "deleted" -> jdbc.update("DELETE FROM teams WHERE id = ?", hidden);
			case "blocked" -> {
				UUID hiddenOwner = UUID.fromString(AuthenticatedTestUser.register(mockMvc, mapper)
						.createProfile("Hidden").id());
				jdbc.update("UPDATE profiles SET status = 'ACTIVE' WHERE id = ?", hiddenOwner);
				jdbc.update("UPDATE team_members SET profile_id = ? WHERE team_id = ?", hiddenOwner, hidden);
				jdbc.update("UPDATE teams SET owner_profile_id = ? WHERE id = ?", hiddenOwner, hidden);
				jdbc.update("INSERT INTO profile_blocks (blocker_profile_id, blocked_profile_id, created_at) VALUES (?, ?, ?)",
						profileId, hiddenOwner, now);
			}
		}
		send(session, ids).andExpect(status().isNoContent());
		assertThat(shown(ids.getFirst())).isNull();
		assertThat(lastShown(hidden)).isNull();
		assertThat(lastShown(available)).isEqualTo(now);
	}

	@Test
	void concurrentSameSessionEventsWriteOnce() throws Exception {
		UUID team = team();
		UUID session = session(List.of(team), true);
		clearInvocations(writer);
		concurrently(session, session);
		verify(writer, times(1)).record(any(), any(), any(), any(), any());
		assertThat(shown(entryIds(session).getFirst())).isEqualTo(now);
		assertThat(lastShown(team)).isEqualTo(now);
	}

	@Test
	void concurrentDifferentSessionsShareHistoryWithoutLosingEntries() throws Exception {
		UUID team = team();
		UUID first = session(List.of(team), true);
		UUID second = session(List.of(team), true);
		concurrently(first, second);
		assertThat(shown(entryIds(first).getFirst())).isEqualTo(now);
		assertThat(shown(entryIds(second).getFirst())).isEqualTo(now);
		assertThat(history.findByOwnerUserIdAndTeamIdIn(userId, List.of(team))).hasSize(1);
	}

	@Test
	void rollbackRevertsBothEntryAndHistory() {
		UUID team = team();
		UUID session = session(List.of(team), true);
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			service.recordForUser(userId, session, entryIds(session));
			throw new IllegalStateException("abort");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(shown(entryIds(session).getFirst())).isNull();
		assertThat(lastShown(team)).isNull();
	}

	@Test
	void clockRollbackDoesNotPrecedeDeliveryOrMoveHistoryBack() throws Exception {
		UUID team = team();
		UUID first = session(List.of(team), true);
		setTime(now.plusMinutes(10));
		send(first, entryIds(first)).andExpect(status().isNoContent());
		UUID second = session(List.of(team), true);
		setTime(now.minusSeconds(1));
		send(second, entryIds(second)).andExpect(status().isNoContent());
		assertThat(shown(entryIds(second).getFirst())).isEqualTo(now);
		assertThat(lastShown(team)).isEqualTo(now.plusMinutes(10));
	}

	@Test
	void fiftyEntriesUseOneEligibilityReadAndOneHistoryWriteWithoutTeamGraphs() throws Exception {
		List<UUID> teams = IntStream.range(0, 50).mapToObj(i -> team()).toList();
		UUID single = session(teams.subList(0, 1), true);
		UUID full = session(teams, true);
		var singleIds = entryIds(single);
		var fullIds = entryIds(full);
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		try {
			statistics.clear();
			service.recordForUser(userId, single, singleIds);
			long count = statistics.getPrepareStatementCount();
			clearInvocations(writer);
			statistics.clear();
			service.recordForUser(userId, full, fullIds);
			assertThat(statistics.getPrepareStatementCount()).isEqualTo(count).isLessThanOrEqualTo(8);
			assertThat(statistics.getEntityLoadCount()).isLessThanOrEqualTo(52);
			verify(writer).lockTeams(any());
			verify(writer).record(any(), any(), any(), any(), any());
			assertThat(history.findByOwnerUserIdAndTeamIdIn(userId, teams)).hasSize(50);
		} finally {
			statistics.setStatisticsEnabled(false);
		}
	}

	private void concurrently(UUID first, UUID second) throws Exception {
		try (var executor = Executors.newFixedThreadPool(2)) {
			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch start = new CountDownLatch(1);
			var futures = new ArrayList<java.util.concurrent.Future<?>>();
			for (UUID session : List.of(first, second)) {
				var ids = entryIds(session);
				futures.add(executor.submit(() -> {
					ready.countDown();
					assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
					service.recordForUser(userId, session, ids);
					return null;
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			for (var future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}
		}
	}

	private UUID session(List<UUID> teams, boolean delivered) {
		UUID session = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO team_recommendation_sessions
				(id, owner_user_id, client_request_id, created_at, expires_at, page_size, entry_count)
				VALUES (?, ?, ?, ?, ?, 1, ?)
				""", session, userId, UUID.randomUUID(), now.minusHours(1), now.plusDays(1), teams.size());
		for (int i = 0; i < teams.size(); i++) {
			jdbc.update("""
					INSERT INTO team_recommendation_entries (id, session_id, ordinal, team_id, first_delivered_at)
					VALUES (?, ?, ?, ?, ?)
					""", UUID.randomUUID(), session, i, teams.get(i), delivered ? now : null);
		}
		return session;
	}

	private UUID team() {
		UUID team = UUID.randomUUID();
		UUID founder = UUID.randomUUID();
		UUID position = UUID.randomUUID();
		UUID request = UUID.randomUUID();
		jdbc.update("INSERT INTO teams (id, owner_profile_id, name, purpose) VALUES (?, ?, 'Team', 'Project')",
				team, ownerProfileId);
		jdbc.update("""
				INSERT INTO team_positions (id, team_id, name, capacity)
				VALUES (?, ?, 'Founder', 1), (?, ?, 'Backend', 1)
				""", founder, team, position, team);
		jdbc.update("INSERT INTO team_members (team_id, profile_id, position_id) VALUES (?, ?, ?)",
				team, ownerProfileId, founder);
		jdbc.update("INSERT INTO team_requests (id, team_id, title, recruitment_deadline) VALUES (?, ?, 'Recruitment', ?)",
				request, team, now.plusDays(2));
		jdbc.update("INSERT INTO team_request_positions (id, request_id, team_position_id) VALUES (?, ?, ?)",
				UUID.randomUUID(), request, position);
		return team;
	}

	private ResultActions send(UUID session, List<UUID> ids) throws Exception {
		return mockMvc.perform(viewer.authorize(post(BASE + session + "/impressions"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("entryIds", ids))));
	}

	private List<UUID> entryIds(UUID session) {
		return entries.findWindow(session, 0, 50).stream().map(TeamRecommendationEntry::getId).toList();
	}

	private LocalDateTime shown(UUID entry) {
		return entries.findById(entry).orElseThrow().getFirstShownAt();
	}

	private LocalDateTime lastShown(UUID team) {
		return history.findByOwnerUserIdAndTeamIdIn(userId, List.of(team)).stream()
				.map(TeamRecommendationImpression::getLastShownAt).findFirst().orElse(null);
	}

	private void setTime(LocalDateTime time) {
		when(clock.getZone()).thenReturn(ZoneId.systemDefault());
		when(clock.instant()).thenReturn(time.atZone(ZoneId.systemDefault()).toInstant());
	}
}
