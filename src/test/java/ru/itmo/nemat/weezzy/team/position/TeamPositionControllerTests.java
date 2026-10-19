package ru.itmo.nemat.weezzy.team.position;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.team.TeamService;
import ru.itmo.nemat.weezzy.team.dto.CreateTeamRequest;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationService;
import ru.itmo.nemat.weezzy.team.position.dto.CreateTeamPositionRequest;
import ru.itmo.nemat.weezzy.team.position.dto.UpdateTeamPositionRequest;
import ru.itmo.nemat.weezzy.team.request.TeamRequestService;
import ru.itmo.nemat.weezzy.team.request.dto.CreateRecruitmentRequest;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false", "app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false"
})
class TeamPositionControllerTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private TeamService teamService;
	@Autowired
	private TeamPositionService positionService;
	@Autowired
	private TeamInvitationService invitationService;
	@Autowired
	private TeamRequestService requestService;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void founderHasExplicitPositionAndPurposeAndOwnerRightsAreSeparate() throws Exception {
		Fixture fixture = fixture();
		mockMvc.perform(fixture.owner().owner().authorize(get("/api/teams/" + fixture.teamId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.purpose").value("Build hackathon project"))
				.andExpect(jsonPath("$.members[0].positionName").value("Backend developer"))
				.andExpect(jsonPath("$.members[0].positionId").isNotEmpty())
				.andExpect(jsonPath("$.positions[0].capacity").value(1))
				.andExpect(jsonPath("$.positions[0].remainingSlotsCount").value(0));
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing-purpose", "blank-purpose", "long-purpose",
			"missing-position", "blank-position", "long-position"})
	void teamCreationRequiresPurposeAndFounderPosition(String invalid) throws Exception {
		TestProfile owner = profile();
		Map<String, Object> body = new HashMap<>(Map.of("name", "Team",
				"purpose", "Project", "ownerPositionName", "Backend"));
		switch (invalid) {
			case "missing-purpose" -> body.remove("purpose");
			case "blank-purpose" -> body.put("purpose", "  ");
			case "long-purpose" -> body.put("purpose", "x".repeat(2001));
			case "missing-position" -> body.remove("ownerPositionName");
			case "blank-position" -> body.put("ownerPositionName", "  ");
			case "long-position" -> body.put("ownerPositionName", "x".repeat(121));
			default -> throw new IllegalArgumentException(invalid);
		}
		mockMvc.perform(owner.owner().authorize(post("/api/teams"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body))).andExpect(status().isBadRequest());
	}

	@Test
	void ownerCreatesEditsAndListsPositionsAndOutsiderCannotWrite() throws Exception {
		Fixture fixture = fixture();
		Map<String, Object> body = body();
		body.put("name", "  Designer  ");
		String response = create(fixture.owner(), fixture.teamId(), body)
				.andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Designer"))
				.andExpect(jsonPath("$.remainingSlotsCount").value(2))
				.andReturn().getResponse().getContentAsString();
		UUID id = UUID.fromString(objectMapper.readTree(response).path("id").asText());
		mockMvc.perform(fixture.owner().owner().authorize(put(base(fixture.teamId()) + "/" + id))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body()))).andExpect(status().isOk());
		TestProfile outsider = profile();
		create(outsider, fixture.teamId(), body()).andExpect(status().isForbidden());
		mockMvc.perform(outsider.owner().authorize(put(base(fixture.teamId()) + "/" + id))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body()))).andExpect(status().isForbidden());
		mockMvc.perform(outsider.owner().authorize(get(base(fixture.teamId()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
		mockMvc.perform(outsider.owner().authorize(get(base(fixture.teamId()) + "/" + id)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
		mockMvc.perform(outsider.owner().authorize(
				get(base(fixture.teamId()) + "/" + UUID.randomUUID())))
				.andExpect(status().isNotFound());
		for (var request : List.of(get(base(fixture.teamId())),
				get(base(fixture.teamId()) + "/" + id),
				post(base(fixture.teamId())).contentType(MediaType.APPLICATION_JSON).content("{}"),
				put(base(fixture.teamId()) + "/" + id)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))) {
			mockMvc.perform(request).andExpect(status().isUnauthorized());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"blank", "long-name", "description", "zero", "negative",
			"missing-skills", "null-skill"})
	void positionInputIsValidated(String invalid) throws Exception {
		Fixture fixture = fixture();
		Map<String, Object> body = body();
		switch (invalid) {
			case "blank" -> body.put("name", "  ");
			case "long-name" -> body.put("name", "x".repeat(121));
			case "description" -> body.put("description", "x".repeat(2001));
			case "zero" -> body.put("capacity", 0);
			case "negative" -> body.put("capacity", -1);
			case "missing-skills" -> body.remove("skillIds");
			case "null-skill" -> body.put("skillIds", java.util.Arrays.asList((Object) null));
			default -> throw new IllegalArgumentException(invalid);
		}
		create(fixture.owner(), fixture.teamId(), body).andExpect(status().isBadRequest());
	}

	@Test
	void invitationsRequirePositionAndCannotUseAnotherTeamsPosition() throws Exception {
		Fixture fixture = fixture();
		Fixture other = fixture();
		TestProfile candidate = profile();
		UUID foreign = position(fixture.owner(), fixture.teamId(), 1);
		mockMvc.perform(other.owner().owner().authorize(
				post("/api/teams/" + other.teamId() + "/invitations"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"inviteeProfileId", candidate.id(), "positionId", foreign))))
				.andExpect(status().isNotFound());
		mockMvc.perform(fixture.owner().owner().authorize(
				post("/api/teams/" + fixture.teamId() + "/invitations"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("inviteeProfileId", candidate.id()))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void capacityCannotShrinkBelowMembershipAndInvitationsClaimLastPlace() throws Exception {
		Fixture fixture = fixture();
		UUID positionId = position(fixture.owner(), fixture.teamId(), 2);
		for (int i = 0; i < 2; i++) {
			TestProfile candidate = profile();
			UUID invitation = invitationService.send(userId(fixture.owner()), fixture.teamId(),
					UUID.fromString(candidate.id()), positionId).id();
			invitationService.accept(userId(candidate), invitation);
		}
		assertThatThrownBy(() -> positionService.updateForUser(userId(fixture.owner()),
				fixture.teamId(), positionId, new UpdateTeamPositionRequest("Designer", null, 1, Set.of())))
				.isInstanceOf(TeamPositionCapacityException.class);
		TestProfile extra = profile();
		assertThatThrownBy(() -> invitationService.send(userId(fixture.owner()), fixture.teamId(),
				UUID.fromString(extra.id()), positionId)).isInstanceOf(TeamPositionCapacityException.class);
	}

	@Test
	void missingSkillRollsBackCreationAndEditing() throws Exception {
		Fixture fixture = fixture();
		Map<String, Object> body = body();
		body.put("skillIds", Set.of(UUID.randomUUID()));
		create(fixture.owner(), fixture.teamId(), body).andExpect(status().isNotFound());
		assertThat(positionService.findForUser(userId(fixture.owner()), fixture.teamId()))
				.hasSize(1);
		UUID skillId = UUID.randomUUID();
		jdbc.update("INSERT INTO skills (id, name, created_at) VALUES (?, ?, NOW())",
				skillId, "Design " + skillId);
		UUID positionId = positionService.createForUser(userId(fixture.owner()), fixture.teamId(),
				new CreateTeamPositionRequest("Designer", null, 2, Set.of(skillId))).id();
		body.put("name", "Changed");
		body.put("capacity", 3);
		mockMvc.perform(fixture.owner().owner().authorize(
				put(base(fixture.teamId()) + "/" + positionId))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body))).andExpect(status().isNotFound());
		var position = positionService.findByIdForUser(
				userId(fixture.owner()), fixture.teamId(), positionId);
		assertThat(position.name()).isEqualTo("Designer");
		assertThat(position.capacity()).isEqualTo(2);
		assertThat(position.skills()).extracting(skill -> skill.id()).containsExactly(skillId);
	}

	@Test
	void closingRecruitmentPreservesPositionAndDirectInvitationCanFillIt() throws Exception {
		Fixture fixture = fixture();
		UUID positionId = position(fixture.owner(), fixture.teamId(), 1);
		UUID requestId = requestService.createForUser(userId(fixture.owner()), fixture.teamId(),
				new CreateRecruitmentRequest("Recruitment", null, LocalDateTime.now().plusDays(1),
						Set.of(positionId))).id();
		requestService.closeForUser(userId(fixture.owner()), requestId);
		TestProfile candidate = profile();
		UUID invitation = invitationService.send(userId(fixture.owner()), fixture.teamId(),
				UUID.fromString(candidate.id()), positionId).id();
		invitationService.accept(userId(candidate), invitation);
		assertThat(jdbc.queryForObject(
				"SELECT position_id FROM team_members WHERE team_id = ? AND profile_id = ?",
				UUID.class, fixture.teamId(), UUID.fromString(candidate.id()))).isEqualTo(positionId);
	}

	private Fixture fixture() throws Exception {
		TestProfile owner = profile();
		UUID id = teamService.createForUser(userId(owner), new CreateTeamRequest(
				"Team", null, "Build hackathon project", "Backend developer")).id();
		return new Fixture(owner, id);
	}

	private TestProfile profile() throws Exception {
		TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile("User");
		jdbc.update("UPDATE profiles SET status = 'ACTIVE' WHERE id = ?", UUID.fromString(profile.id()));
		return profile;
	}

	private UUID position(TestProfile owner, UUID teamId, int capacity) {
		return positionService.createForUser(userId(owner), teamId,
				new CreateTeamPositionRequest("Designer", null, capacity, Set.of())).id();
	}

	private ResultActions create(TestProfile actor, UUID teamId, Map<String, Object> body)
			throws Exception {
		return mockMvc.perform(actor.owner().authorize(post(base(teamId)))
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
	}

	private Map<String, Object> body() {
		return new HashMap<>(Map.of("name", "Designer", "capacity", 2, "skillIds", List.of()));
	}

	private UUID userId(TestProfile profile) {
		return UUID.fromString(profile.owner().userId());
	}

	private String base(UUID id) {
		return "/api/teams/" + id + "/positions";
	}

	private record Fixture(TestProfile owner, UUID teamId) {
	}
}
