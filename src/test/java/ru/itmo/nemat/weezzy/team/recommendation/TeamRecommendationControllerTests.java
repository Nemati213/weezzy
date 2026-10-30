package ru.itmo.nemat.weezzy.team.recommendation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import ru.itmo.nemat.weezzy.team.recommendation.page.TeamRecommendationCursorCodec;
import ru.itmo.nemat.weezzy.team.request.TeamRequestService;
import ru.itmo.nemat.weezzy.team.request.dto.CreateRecruitmentRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.itmo.nemat.weezzy.support.ProfilePhotoTestData.insertReadyPhoto;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false", "app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false", "app.team-recommendation.max-active-sessions-per-user=3"
})
class TeamRecommendationControllerTests {
	private static final String BASE = "/api/team-recommendations/me/sessions";
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private TeamService teams;
	@Autowired private TeamRequestService requests;
	@Autowired private TeamRecommendationCursorCodec cursors;
	@Autowired private AccountSanctionRepository sanctions;
	@MockitoBean private Clock clock;
	private AuthenticatedTestUser viewer;
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
	}

	@Test
	void createsFirstPageWithStableEntryAndMemberProfileLinksWithoutContacts() throws Exception {
		var fixture = fixture();
		UUID photo = insertReadyPhoto(jdbc, UUID.fromString(fixture.owner().id()));
		UUID key = UUID.randomUUID();
		var result = create(viewer, body(key, 1)).andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("private")))
				.andExpect(jsonPath("$.pageSize").value(1))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].team.id").value(fixture.team().toString()))
				.andExpect(jsonPath("$.items[0].recommendedTeamPositionId").value(fixture.position().toString()))
				.andExpect(jsonPath("$.items[0].options[0].position.teamPositionId").value(fixture.position().toString()))
				.andExpect(jsonPath("$.items[0].options[0].request.id").value(fixture.request().toString()))
				.andExpect(jsonPath("$.items[0].team.members[0].profileId").value(fixture.owner().id()))
				.andExpect(jsonPath("$.items[0].team.members[0].photos[0].id").value(photo.toString()))
				.andExpect(jsonPath("$.nextCursor").isEmpty())
				.andExpect(jsonPath("$.previousCursor").isEmpty());
		var response = result.andReturn().getResponse();
		JsonNode page = objectMapper.readTree(response.getContentAsString());
		String session = page.path("sessionId").asText();
		assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(BASE + "/" + session);
		assertThat(response.getContentAsString()).doesNotContain(
				"\"userId\"", "\"email\"", "\"telegram\"", "\"clientRequestId\"", "\"totalElements\"");
		assertThat(jdbc.queryForObject("""
				SELECT owner_user_id FROM team_recommendation_sessions WHERE id = ?
				""", UUID.class, UUID.fromString(session))).isEqualTo(UUID.fromString(viewer.userId()));
		assertThat(jdbc.queryForObject("""
				SELECT first_delivered_at FROM team_recommendation_entries WHERE id = ?
				""", LocalDateTime.class, UUID.fromString(page.path("items").get(0).path("entryId").asText())))
				.isEqualTo(now);
		read(viewer, session, null)
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("private")))
				.andExpect(jsonPath("$.items[0].entryId").value(page.path("items").get(0).path("entryId").asText()));
		mockMvc.perform(viewer.authorize(get("/api/profiles/" + fixture.owner().id())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(fixture.owner().id()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", ",\"pageSize\":null"})
	void defaultsMissingOrNullPageSizeToTwentyAndAllowsEmptyProfile(String suffix) throws Exception {
		create(viewer, "{\"clientRequestId\":\"" + UUID.randomUUID() + "\"" + suffix + "}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.pageSize").value(20))
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.pageCursor").isString());
	}

	@Test
	void acceptsMaximumPageSize() throws Exception {
		create(viewer, body(UUID.randomUUID(), 50)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.pageSize").value(50));
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 0, 51})
	void rejectsOutOfRangePageSizesWithoutSavingSession(int size) throws Exception {
		create(viewer, body(UUID.randomUUID(), size)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message", containsString("pageSize")));
		assertThat(sessionCount()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"clientRequestId\":null}",
			"{\"clientRequestId\":\"invalid\"}", "null", "{", ""})
	void rejectsMissingKeyOrMalformedBodyWithoutSavingSession(String body) throws Exception {
		create(viewer, body).andExpect(status().isBadRequest());
		assertThat(sessionCount()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "Bearer invalid"})
	void bothEndpointsRequireValidJwt(String authorization) throws Exception {
		mockMvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, authorization)
						.contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID(), 20)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get(BASE + "/" + UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, authorization))
				.andExpect(status().isUnauthorized());
		assertThat(sessionCount()).isZero();
	}

	@Test
	void movesForwardBackwardAndRestoresByPageCursor() throws Exception {
		fixture();
		fixture();
		fixture();
		var first = json(create(viewer, body(UUID.randomUUID(), 1)).andExpect(status().isCreated()));
		String session = first.path("sessionId").asText();
		var second = json(read(viewer, session, first.path("nextCursor").asText()).andExpect(status().isOk()));
		var last = json(read(viewer, session, second.path("nextCursor").asText()).andExpect(status().isOk()));
		assertThat(last.path("nextCursor").isNull()).isTrue();
		assertThat(json(read(viewer, session, last.path("previousCursor").asText())))
				.isEqualTo(second);
		assertThat(json(read(viewer, session, second.path("previousCursor").asText())))
				.isEqualTo(first);
		assertThat(json(read(viewer, session, second.path("pageCursor").asText())))
				.isEqualTo(second);
		assertThat(Set.of(first.path("items").get(0).path("entryId").asText(),
				second.path("items").get(0).path("entryId").asText(),
				last.path("items").get(0).path("entryId").asText())).hasSize(3);
	}

	@Test
	void retryReturnsCurrentFirstPageWithoutExtendingSessionOrRebuildingOrder() throws Exception {
		var fixture = fixture();
		UUID key = UUID.randomUUID();
		var first = json(create(viewer, body(key, 1)).andExpect(status().isCreated()));
		requests.closeForUser(UUID.fromString(fixture.owner().owner().userId()), fixture.request());
		fixture();
		setTime(now.plusHours(1));
		create(viewer, body(key, 1)).andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.LOCATION, BASE + "/" + first.path("sessionId").asText()))
				.andExpect(jsonPath("$.sessionId").value(first.path("sessionId").asText()))
				.andExpect(jsonPath("$.createdAt").value(first.path("createdAt").asText()))
				.andExpect(jsonPath("$.expiresAt").value(first.path("expiresAt").asText()))
				.andExpect(jsonPath("$.pageCursor").value(first.path("pageCursor").asText()))
				.andExpect(jsonPath("$.items").isEmpty());
		assertThat(sessionCount()).isEqualTo(1);
		create(viewer, body(UUID.randomUUID(), 1)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.items", hasSize(1)));
	}

	@Test
	void reusedKeyWithDifferentSizeConflictsButSameKeyForAnotherUserIsIndependent() throws Exception {
		UUID key = UUID.randomUUID();
		var first = json(create(viewer, body(key, 20)).andExpect(status().isCreated()));
		create(viewer, body(key, 1)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
		var other = AuthenticatedTestUser.register(mockMvc, objectMapper);
		var independent = json(create(other, body(key, 1)).andExpect(status().isCreated()));
		assertThat(independent.path("sessionId")).isNotEqualTo(first.path("sessionId"));
		assertThat(sessionCount()).isEqualTo(1);
	}

	@Test
	void hidesForeignAndMissingSessionsBeforeCheckingCursorAndRejectsForeignCursor() throws Exception {
		var own = json(create(viewer, body(UUID.randomUUID(), 20)).andExpect(status().isCreated()));
		var other = AuthenticatedTestUser.register(mockMvc, objectMapper);
		String id = own.path("sessionId").asText();
		read(other, id, "invalid").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
		read(viewer, UUID.randomUUID().toString(), "invalid").andExpect(status().isNotFound());
		read(viewer, id, cursors.encode(UUID.randomUUID(), 0)).andExpect(status().isBadRequest());
		read(viewer, "not-a-uuid", null).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "invalid", "oversized", "unaligned", "out-of-range"})
	void rejectsInvalidCursor(String input) throws Exception {
		var first = json(create(viewer, body(UUID.randomUUID(), 20)).andExpect(status().isCreated()));
		String id = first.path("sessionId").asText();
		String cursor = switch (input) {
			case "oversized" -> "A".repeat(513);
			case "unaligned" -> cursors.encode(UUID.fromString(id), 1);
			case "out-of-range" -> cursors.encode(UUID.fromString(id), 20);
			default -> input;
		};
		read(viewer, id, cursor).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void expiredSessionReturnsNotFoundOnReadAndRetry() throws Exception {
		UUID key = UUID.randomUUID();
		var page = json(create(viewer, body(key, 20)).andExpect(status().isCreated()));
		setTime(now.plusDays(1));
		read(viewer, page.path("sessionId").asText(), null).andExpect(status().isNotFound());
		create(viewer, body(key, 20)).andExpect(status().isNotFound());
		assertThat(sessionCount()).isEqualTo(1);
	}

	@Test
	void evictedSessionReturnsNotFound() throws Exception {
		var first = json(create(viewer, body(UUID.randomUUID(), 20)).andExpect(status().isCreated()));
		for (int i = 1; i <= 3; i++) {
			setTime(now.plusMinutes(i));
			create(viewer, body(UUID.randomUUID(), 20)).andExpect(status().isCreated());
		}
		read(viewer, first.path("sessionId").asText(), null).andExpect(status().isNotFound());
		assertThat(sessionCount()).isEqualTo(3);
	}

	@Test
	void sanctionsBlockBothEndpointsIncludingEmptySessionAndRetry() throws Exception {
		UUID key = UUID.randomUUID();
		var first = json(create(viewer, body(key, 20)).andExpect(status().isCreated()));
		var sanction = new AccountSanction();
		sanction.setTargetUserId(UUID.fromString(viewer.userId()));
		sanction.setCreatedByUserId(UUID.fromString(viewer.userId()));
		sanction.setType(AccountSanctionType.PERMANENT_BAN);
		sanction.setReason("Test");
		sanctions.saveAndFlush(sanction);
		read(viewer, first.path("sessionId").asText(), null).andExpect(status().isForbidden());
		create(viewer, body(key, 20)).andExpect(status().isForbidden());
		create(viewer, body(UUID.randomUUID(), 20)).andExpect(status().isForbidden());
		assertThat(sessionCount()).isEqualTo(1);
	}

	private ResultActions create(AuthenticatedTestUser user, String body) throws Exception {
		return mockMvc.perform(user.authorize(post(BASE))
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions read(AuthenticatedTestUser user, String session, String cursor) throws Exception {
		var request = get(BASE + "/" + session);
		if (cursor != null) {
			request.param("cursor", cursor);
		}
		return mockMvc.perform(user.authorize(request));
	}

	private JsonNode json(ResultActions result) throws Exception {
		return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private String body(UUID key, int pageSize) {
		return "{\"clientRequestId\":\"%s\",\"pageSize\":%d}".formatted(key, pageSize);
	}

	private long sessionCount() {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM team_recommendation_sessions WHERE owner_user_id = ?
				""", Long.class, UUID.fromString(viewer.userId()));
	}

	private void setTime(LocalDateTime value) {
		ZoneId zone = ZoneId.systemDefault();
		when(clock.getZone()).thenReturn(zone);
		when(clock.instant()).thenReturn(value.atZone(zone).toInstant());
	}

	private Fixture fixture() throws Exception {
		var owner = AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile("Owner");
		UUID userId = UUID.fromString(owner.owner().userId());
		jdbc.update("UPDATE profiles SET status = 'ACTIVE' WHERE id = ?", UUID.fromString(owner.id()));
		UUID team = teams.createForUser(userId,
				new CreateTeamRequest("Team", "Description", "Build a project", "Founder")).id();
		UUID position = TeamPositionTestData.createPosition(jdbc, team, "Developer", 1);
		UUID request = requests.createForUser(userId, team, new CreateRecruitmentRequest(
				"Recruitment", "Description", now.plusDays(2), Set.of(position))).id();
		return new Fixture(owner, team, position, request);
	}

	private record Fixture(TestProfile owner, UUID team, UUID position, UUID request) {
	}
}
