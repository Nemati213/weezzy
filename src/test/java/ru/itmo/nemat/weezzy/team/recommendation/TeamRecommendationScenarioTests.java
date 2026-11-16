package ru.itmo.nemat.weezzy.team.recommendation;

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
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanction;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionRepository;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.support.TeamPositionTestData;
import ru.itmo.nemat.weezzy.team.TeamService;
import ru.itmo.nemat.weezzy.team.dto.CreateTeamRequest;
import ru.itmo.nemat.weezzy.team.recommendation.page.TeamRecommendationPageService;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionService;
import ru.itmo.nemat.weezzy.team.request.TeamRequestService;
import ru.itmo.nemat.weezzy.team.request.dto.CreateRecruitmentRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false", "app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false", "app.team-recommendation.max-active-sessions-per-user=3"
})
class TeamRecommendationScenarioTests {
	private static final String BASE = "/api/team-recommendations/me/sessions";
	private static final String PASSWORD = "password123";
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private TeamService teams;
	@Autowired private TeamRequestService requests;
	@Autowired private TeamRecommendationSessionService sessionService;
	@Autowired private AccountSanctionRepository sanctions;
	@MockitoSpyBean private TeamRecommendationPageService pages;
	@MockitoBean private Clock clock;
	private AuthenticatedTestUser viewer;
	private TestProfile viewerProfile;
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
		viewer = AuthenticatedTestUser.register(mockMvc, objectMapper);
		viewerProfile = activeProfile(viewer, "Viewer");
	}

	@Test
	void chosenOptionGoesThroughApplicationAndAcceptanceToThatPosition() throws Exception {
		var fixture = fixture(2);
		var page = json(create(body(UUID.randomUUID(), 20)).andExpect(status().isCreated()));
		JsonNode item = page.path("items").get(0);
		String entryId = item.path("entryId").asText();
		UUID main = UUID.fromString(item.path("recommendedTeamPositionId").asText());
		JsonNode chosen = option(item, fixture.positions().stream()
				.filter(position -> !position.equals(main)).findFirst().orElseThrow());
		UUID chosenPosition = UUID.fromString(chosen.path("position").path("teamPositionId").asText());

		String application = json(mockMvc.perform(viewer.authorize(post(
						"/api/team-request-positions/" + chosen.path("position").path("id").asText()
								+ "/applications"))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isCreated())).path("id").asText();

		var pending = json(read(page.path("sessionId").asText(), page.path("pageCursor").asText()));
		assertThat(pending.path("items").get(0).path("entryId").asText()).isEqualTo(entryId);
		assertThat(pending.path("items").get(0).path("options")).hasSize(1);
		assertThat(pending.path("items").get(0).path("recommendedTeamPositionId").asText())
				.isEqualTo(main.toString());

		mockMvc.perform(fixture.owner().owner().authorize(
						post("/api/team-applications/" + application + "/accept")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"));
		assertThat(jdbc.queryForObject("""
				SELECT position_id FROM team_members WHERE team_id = ? AND profile_id = ?
				""", UUID.class, fixture.team(), UUID.fromString(viewerProfile.id())))
				.isEqualTo(chosenPosition);
		read(page.path("sessionId").asText(), null).andExpect(jsonPath("$.items").isEmpty());

		mockMvc.perform(viewer.authorize(delete("/api/teams/" + fixture.team() + "/members/me")))
				.andExpect(status().isNoContent());
		var returned = json(read(page.path("sessionId").asText(), null));
		assertThat(returned.path("items").get(0).path("entryId").asText()).isEqualTo(entryId);
		assertThat(returned.path("items").get(0).path("options")).hasSize(2);
	}

	@Test
	void invitationTakesLastPlaceThenCapacityIncreaseLetsPendingApplicationJoin() throws Exception {
		var fixture = fixture(1);
		UUID position = fixture.positions().getFirst();
		var page = json(create(body(UUID.randomUUID(), 20)).andExpect(status().isCreated()));
		String session = page.path("sessionId").asText();
		String entryId = page.path("items").get(0).path("entryId").asText();
		String application = apply(option(page.path("items").get(0), position))
				.andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");

		var invited = AuthenticatedTestUser.register(mockMvc, objectMapper);
		var invitedProfile = activeProfile(invited, "Invited");
		String invitation = json(mockMvc.perform(fixture.owner().owner().authorize(
								post("/api/teams/" + fixture.team() + "/invitations"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"inviteeProfileId\":\"%s\",\"positionId\":\"%s\"}"
								.formatted(invitedProfile.id(), position)))
				.andExpect(status().isCreated())).path("id").asText();
		mockMvc.perform(invited.authorize(post("/api/team-invitations/" + invitation + "/accept")))
				.andExpect(status().isOk());
		mockMvc.perform(fixture.owner().owner().authorize(post(application + "/accept")))
				.andExpect(status().isConflict());
		read(session, null).andExpect(jsonPath("$.items").isEmpty());

		mockMvc.perform(fixture.owner().owner().authorize(
						put("/api/teams/" + fixture.team() + "/positions/" + position))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Developer\",\"capacity\":2,\"skillIds\":[]}"))
				.andExpect(status().isOk());
		read(session, null).andExpect(jsonPath("$.items").isEmpty());
		mockMvc.perform(fixture.owner().owner().authorize(post(application + "/accept")))
				.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM team_members WHERE position_id = ?
				""", Long.class, position)).isEqualTo(2);
		mockMvc.perform(fixture.owner().owner().authorize(
						put("/api/teams/" + fixture.team() + "/positions/" + position))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Developer\",\"capacity\":3,\"skillIds\":[]}"))
				.andExpect(status().isOk());
		mockMvc.perform(viewer.authorize(delete("/api/teams/" + fixture.team() + "/members/me")))
				.andExpect(status().isNoContent());
		var restored = json(read(session, null));
		assertThat(restored.path("items").get(0).path("entryId").asText()).isEqualTo(entryId);
		assertThat(restored.path("items").get(0).path("options").get(0)
				.path("position").path("remainingSlotsCount").asLong()).isEqualTo(2);
	}

	@ParameterizedTest
	@ValueSource(strings = {"closed", "archived", "deadline", "blocked", "sanctioned-owner",
			"deleted-owner", "pending", "full"})
	void changesBetweenPagesLeaveEmptySlotWithoutShiftingWindows(String reason) throws Exception {
		var byTeam = new HashMap<UUID, Fixture>();
		for (int i = 0; i < 3; i++) {
			var fixture = fixture(1);
			byTeam.put(fixture.team(), fixture);
		}
		var first = json(create(body(UUID.randomUUID(), 1)).andExpect(status().isCreated()));
		String session = first.path("sessionId").asText();
		var second = byTeam.get(teamAt(session, 1));
		var third = byTeam.get(teamAt(session, 2));
		UUID ownerUser = UUID.fromString(second.owner().owner().userId());

		switch (reason) {
			case "closed" -> requests.closeForUser(ownerUser, second.request());
			case "archived" -> mockMvc.perform(second.owner().owner().authorize(
					post("/api/teams/" + second.team() + "/archive")))
					.andExpect(status().isNoContent());
			case "deadline" -> {
				jdbc.update("UPDATE team_requests SET recruitment_deadline = ? WHERE id = ?",
						now.plusHours(1), second.request());
				setTime(now.plusHours(1));
			}
			case "blocked" -> mockMvc.perform(viewer.authorize(
					post("/api/blocks/" + second.owner().id()))).andExpect(status().isOk());
			case "sanctioned-owner" -> ban(ownerUser);
			case "deleted-owner" -> mockMvc.perform(second.owner().owner().authorize(
							delete("/api/users/me")).contentType(MediaType.APPLICATION_JSON)
					.content("{\"currentPassword\":\"" + PASSWORD + "\"}"))
					.andExpect(status().isNoContent());
			case "pending" -> apply(json(read(session, first.path("nextCursor").asText()))
					.path("items").get(0).path("options").get(0)).andExpect(status().isCreated());
			case "full" -> {
				var other = AuthenticatedTestUser.register(mockMvc, objectMapper);
				var otherProfile = activeProfile(other, "Other");
				String invitation = json(mockMvc.perform(second.owner().owner().authorize(
										post("/api/teams/" + second.team() + "/invitations"))
								.contentType(MediaType.APPLICATION_JSON)
								.content("{\"inviteeProfileId\":\"%s\",\"positionId\":\"%s\"}"
										.formatted(otherProfile.id(), second.positions().getFirst())))
						.andExpect(status().isCreated())).path("id").asText();
				mockMvc.perform(other.authorize(post("/api/team-invitations/" + invitation + "/accept")))
						.andExpect(status().isOk());
			}
			default -> throw new IllegalArgumentException(reason);
		}

		var middle = json(read(session, first.path("nextCursor").asText()));
		assertThat(middle.path("items")).isEmpty();
		assertThat(middle.path("previousCursor").asText()).isEqualTo(first.path("pageCursor").asText());
		var last = json(read(session, middle.path("nextCursor").asText()));
		assertThat(last.path("items").get(0).path("team").path("id").asText())
				.isEqualTo(third.team().toString());
		assertThat(last.path("nextCursor").isNull()).isTrue();
		assertThat(json(read(session, middle.path("previousCursor").asText())).path("items").get(0)
				.path("entryId").asText()).isEqualTo(first.path("items").get(0).path("entryId").asText());
	}

	@Test
	void longJourneyBackwardAndRestoreAfterLostCacheKeepTheSameWindows() throws Exception {
		for (int i = 0; i < 7; i++) {
			fixture(1);
		}
		UUID key = UUID.randomUUID();
		var forward = new ArrayList<JsonNode>();
		forward.add(json(create(body(key, 2)).andExpect(status().isCreated())));
		String session = forward.getFirst().path("sessionId").asText();
		while (!forward.getLast().path("nextCursor").isNull()) {
			forward.add(json(read(session, forward.getLast().path("nextCursor").asText())));
		}
		assertThat(forward).hasSize(4);
		var entries = new LinkedHashSet<String>();
		forward.forEach(page -> page.path("items").forEach(
				item -> entries.add(item.path("entryId").asText())));
		assertThat(entries).hasSize(7);

		mockMvc.perform(viewer.authorize(post(BASE + "/" + session + "/impressions"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"entryIds\":[\"%s\",\"%s\"]}".formatted(
								forward.get(1).path("items").get(0).path("entryId").asText(),
								forward.get(1).path("items").get(1).path("entryId").asText())))
				.andExpect(status().isNoContent());

		JsonNode current = forward.getLast();
		for (int index = forward.size() - 2; index >= 0; index--) {
			current = json(read(session, current.path("previousCursor").asText()));
			assertThat(current).isEqualTo(forward.get(index));
		}
		assertThat(current.path("previousCursor").isNull()).isTrue();
		assertThat(json(read(session, forward.get(2).path("pageCursor").asText())))
				.isEqualTo(forward.get(2));
		assertThat(json(create(body(key, 2)).andExpect(status().isOk()))).isEqualTo(forward.getFirst());
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM team_recommendation_entries
				WHERE session_id = ? AND first_delivered_at IS NOT NULL
				""", Long.class, UUID.fromString(session))).isEqualTo(7);
	}

	@Test
	void sessionEvictedBetweenCreationAndFirstPageReturnsNotFoundAndKeyCanBeReused()
			throws Exception {
		fixture(1);
		UUID userId = UUID.fromString(viewer.userId());
		doAnswer(invocation -> {
			try (var executor = Executors.newSingleThreadExecutor()) {
				for (int i = 1; i <= 3; i++) {
					setTime(now.plusMinutes(i));
					executor.submit(() -> sessionService.createForUser(userId, UUID.randomUUID(), 20))
							.get(10, TimeUnit.SECONDS);
				}
			}
			return invocation.callRealMethod();
		}).doCallRealMethod().when(AopTestUtils.<TeamRecommendationPageService>getUltimateTargetObject(pages))
				.findForUser(any(), any(), any());
		UUID key = UUID.randomUUID();

		create(body(key, 20)).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM team_recommendation_sessions
				WHERE owner_user_id = ? AND client_request_id = ?
				""", Long.class, userId, key)).isZero();

		setTime(now.plusMinutes(4));
		create(body(key, 20)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.items", hasSize(1)));
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM team_recommendation_sessions WHERE owner_user_id = ?
				""", Long.class, userId)).isEqualTo(3);
	}

	@Test
	void deletingViewerAccountRemovesSessionsEntriesAndHistory() throws Exception {
		fixture(1);
		var page = json(create(body(UUID.randomUUID(), 20)).andExpect(status().isCreated()));
		String session = page.path("sessionId").asText();
		mockMvc.perform(viewer.authorize(post(BASE + "/" + session + "/impressions"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"entryIds\":[\"%s\"]}".formatted(
								page.path("items").get(0).path("entryId").asText())))
				.andExpect(status().isNoContent());
		UUID userId = UUID.fromString(viewer.userId());

		mockMvc.perform(viewer.authorize(delete("/api/users/me"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"currentPassword\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("""
				SELECT (SELECT COUNT(*) FROM team_recommendation_sessions WHERE owner_user_id = ?)
				     + (SELECT COUNT(*) FROM team_recommendation_entries WHERE session_id = ?)
				     + (SELECT COUNT(*) FROM team_recommendation_impressions WHERE owner_user_id = ?)
				""", Long.class, userId, UUID.fromString(session), userId)).isZero();
		read(session, null).andExpect(status().isUnauthorized());
	}

	private ResultActions create(String body) throws Exception {
		return mockMvc.perform(viewer.authorize(post(BASE))
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions read(String session, String cursor) throws Exception {
		var request = get(BASE + "/" + session);
		if (cursor != null) {
			request.param("cursor", cursor);
		}
		return mockMvc.perform(viewer.authorize(request));
	}

	private ResultActions apply(JsonNode option) throws Exception {
		return mockMvc.perform(viewer.authorize(post("/api/team-request-positions/"
						+ option.path("position").path("id").asText() + "/applications"))
				.contentType(MediaType.APPLICATION_JSON).content("{}"));
	}

	private JsonNode option(JsonNode item, UUID teamPositionId) {
		for (JsonNode option : item.path("options")) {
			if (option.path("position").path("teamPositionId").asText()
					.equals(teamPositionId.toString())) {
				return option;
			}
		}
		throw new AssertionError("No option for position " + teamPositionId);
	}

	private UUID teamAt(String session, int ordinal) {
		return jdbc.queryForObject("""
				SELECT team_id FROM team_recommendation_entries WHERE session_id = ? AND ordinal = ?
				""", UUID.class, UUID.fromString(session), ordinal);
	}

	private JsonNode json(ResultActions result) throws Exception {
		return objectMapper.readTree(result.andExpect(status().is2xxSuccessful())
				.andReturn().getResponse().getContentAsString());
	}

	private String body(UUID key, int pageSize) {
		return "{\"clientRequestId\":\"%s\",\"pageSize\":%d}".formatted(key, pageSize);
	}

	private void ban(UUID userId) {
		var sanction = new AccountSanction();
		sanction.setTargetUserId(userId);
		sanction.setCreatedByUserId(userId);
		sanction.setType(AccountSanctionType.PERMANENT_BAN);
		sanction.setReason("Test");
		sanctions.saveAndFlush(sanction);
	}

	private void setTime(LocalDateTime value) {
		ZoneId zone = ZoneId.systemDefault();
		when(clock.getZone()).thenReturn(zone);
		when(clock.instant()).thenReturn(value.atZone(zone).toInstant());
	}

	private TestProfile activeProfile(AuthenticatedTestUser user, String name) throws Exception {
		var profile = user.createProfile(name);
		jdbc.update("UPDATE profiles SET status = 'ACTIVE' WHERE id = ?", UUID.fromString(profile.id()));
		return profile;
	}

	private Fixture fixture(int positions) throws Exception {
		var owner = activeProfile(AuthenticatedTestUser.register(mockMvc, objectMapper), "Owner");
		UUID userId = UUID.fromString(owner.owner().userId());
		UUID team = teams.createForUser(userId,
				new CreateTeamRequest("Team", "Description", "Build a project", "Founder")).id();
		var ids = new ArrayList<UUID>();
		for (int i = 0; i < positions; i++) {
			ids.add(TeamPositionTestData.createPosition(jdbc, team, "Developer " + i, 1));
		}
		UUID request = requests.createForUser(userId, team, new CreateRecruitmentRequest(
				"Recruitment", "Description", now.plusDays(2), new LinkedHashSet<>(ids))).id();
		return new Fixture(owner, team, List.copyOf(ids), request);
	}

	private record Fixture(TestProfile owner, UUID team, List<UUID> positions, UUID request) {
	}
}
