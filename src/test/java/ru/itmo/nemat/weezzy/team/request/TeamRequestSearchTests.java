package ru.itmo.nemat.weezzy.team.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanction;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionRepository;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.team.TeamService;
import ru.itmo.nemat.weezzy.team.dto.CreateTeamRequest;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationService;
import ru.itmo.nemat.weezzy.team.position.TeamPositionService;
import ru.itmo.nemat.weezzy.team.position.dto.CreateTeamPositionRequest;
import ru.itmo.nemat.weezzy.team.request.dto.CreateRecruitmentRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false", "app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false"
})
class TeamRequestSearchTests {
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
	private TeamRequestService requestService;
	@Autowired
	private TeamInvitationService invitationService;
	@Autowired
	private ProfileBlockService blockService;
	@Autowired
	private AccountSanctionRepository sanctionRepository;

	private TestProfile viewer;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void setUp() throws Exception {
		jdbc.update("DELETE FROM team_requests");
		viewer = profile();
	}

	@Test
	void requiresAuthenticationAndAllowsBrowsingWithoutProfile() throws Exception {
		Fixture fixture = fixture();
		mockMvc.perform(get("/api/team-requests")).andExpect(status().isUnauthorized());
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		mockMvc.perform(user.authorize(get("/api/team-requests")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(fixture.requestId().toString()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"title", "description", "team-name", "purpose",
			"position-name", "position-description"})
	void textSearchMatchesAllDocumentedFieldsIgnoringCase(String field) throws Exception {
		Fixture fixture = fixture();
		String text = "Поиск SearchNeedle";
		switch (field) {
			case "title", "description" -> jdbc.update(
					"UPDATE team_requests SET " + field + " = ? WHERE id = ?",
					text, fixture.requestId());
			case "team-name", "purpose" -> jdbc.update(
					"UPDATE teams SET " + (field.equals("purpose") ? "purpose" : "name")
							+ " = ? WHERE id = ?", text, fixture.teamId());
			case "position-name", "position-description" -> jdbc.update(
					"UPDATE team_positions SET "
							+ (field.equals("position-name") ? "name" : "description")
							+ " = ? WHERE id = ?", text, fixture.positionId());
			default -> throw new IllegalArgumentException(field);
		}
		search("  searchNEEDLE  ").andExpect(jsonPath("$.totalElements").value(1));
		search("ПОИСК").andExpect(jsonPath("$.totalElements").value(1));
		search("absent").andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void wildcardCharactersAreLiteralAndBlankQueryMeansNoTextFilter() throws Exception {
		Fixture fixture = fixture();
		jdbc.update("UPDATE team_requests SET title = ? WHERE id = ?",
				"100%_! project", fixture.requestId());
		search("%_!").andExpect(jsonPath("$.totalElements").value(1));
		search("%missing").andExpect(jsonPath("$.totalElements").value(0));
		search("   ").andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void allSelectedSkillsMustBelongToOneFreeAdvertisedPosition() throws Exception {
		Fixture fixture = fixture();
		UUID java = skill("Java");
		UUID design = skill("Design");
		UUID first = position(fixture.owner(), fixture.teamId(), Set.of(java));
		UUID second = position(fixture.owner(), fixture.teamId(), Set.of(design));
		UUID request = advertise(fixture.owner(), fixture.teamId(), Set.of(first, second));
		searchSkills(java, design).andExpect(jsonPath("$.totalElements").value(0));
		UUID combined = position(fixture.owner(), fixture.teamId(), Set.of(java, design));
		UUID matching = advertise(fixture.owner(), fixture.teamId(), Set.of(combined));
		searchSkills(java, design).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(matching.toString()));
		searchSkills(java).andExpect(jsonPath("$.totalElements").value(2));
		searchSkills(UUID.randomUUID()).andExpect(jsonPath("$.totalElements").value(0));
		assertThat(request).isNotEqualTo(fixture.requestId());
		TestProfile candidate = profile();
		join(fixture, candidate, combined);
		searchSkills(java, design).andExpect(jsonPath("$.totalElements").value(0));
		teamService.leaveForUser(userId(candidate), fixture.teamId());
		searchSkills(java, design).andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void textAndSkillsMustMatchTheSameFreePosition() throws Exception {
		Fixture fixture = fixture();
		UUID java = skill("Java");
		UUID javaPosition = positionService.createForUser(userId(fixture.owner()),
				fixture.teamId(), new CreateTeamPositionRequest(
						"Developer", null, 1, Set.of(java))).id();
		UUID designPosition = positionService.createForUser(userId(fixture.owner()),
				fixture.teamId(), new CreateTeamPositionRequest(
						"Illustrator", null, 1, Set.of())).id();
		advertise(fixture.owner(), fixture.teamId(), Set.of(javaPosition, designPosition));
		mockMvc.perform(viewer.owner().authorize(get("/api/team-requests"))
				.param("query", "Illustrator").param("skillIds", java.toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		join(fixture, profile(), designPosition);
		search("Illustrator").andExpect(jsonPath("$.totalElements").value(0));
	}

	@ParameterizedTest
	@ValueSource(strings = {"closed", "expired", "archived", "draft-owner",
			"deleted-owner", "missing-owner", "ban", "suspension", "block-out", "block-in",
			"full"})
	void unavailableRequestsAreFilteredBeforePagination(String reason) throws Exception {
		Fixture hidden = fixture();
		Fixture visible = fixture();
		switch (reason) {
			case "closed" -> requestService.closeForUser(userId(hidden.owner()), hidden.requestId());
			case "expired" -> jdbc.update(
					"UPDATE team_requests SET recruitment_deadline = ? WHERE id = ?",
					LocalDateTime.now().minusSeconds(1), hidden.requestId());
			case "archived" -> teamService.archiveForUser(userId(hidden.owner()), hidden.teamId());
			case "draft-owner" -> jdbc.update("UPDATE profiles SET status = 'DRAFT' WHERE id = ?",
					profileId(hidden.owner()));
			case "deleted-owner" -> jdbc.update(
					"UPDATE profiles SET user_id = NULL WHERE id = ?", profileId(hidden.owner()));
			case "missing-owner" -> jdbc.update(
					"DELETE FROM team_members WHERE team_id = ? AND profile_id = ?",
					hidden.teamId(), profileId(hidden.owner()));
			case "ban", "suspension" -> restrict(hidden.owner(), reason.equals("ban"));
			case "block-out" -> blockService.block(profileId(viewer), profileId(hidden.owner()));
			case "block-in" -> blockService.block(profileId(hidden.owner()), profileId(viewer));
			case "full" -> join(hidden, profile(), hidden.positionId());
			default -> throw new IllegalArgumentException(reason);
		}
		mockMvc.perform(viewer.owner().authorize(get("/api/team-requests")).param("size", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1))
				.andExpect(jsonPath("$.hasNext").value(false))
				.andExpect(jsonPath("$.content[0].id").value(visible.requestId().toString()));
	}

	@Test
	void ownAndJoinedTeamsAreExcludedButTheirCardsRemainReadable() throws Exception {
		Fixture fixture = fixture();
		join(fixture, viewer, fixture.positionId());
		UUID spare = position(fixture.owner(), fixture.teamId(), Set.of());
		advertise(fixture.owner(), fixture.teamId(), Set.of(spare));
		search("").andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(fixture.owner().owner().authorize(get("/api/team-requests")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(viewer.owner().authorize(get("/api/team-requests/" + fixture.requestId())))
				.andExpect(status().isOk());
	}

	@Test
	void expiredAndRevokedSanctionsDoNotHideRequests() throws Exception {
		Fixture fixture = fixture();
		restrict(fixture.owner(), false);
		jdbc.update("UPDATE account_sanctions SET expires_at = ? WHERE target_user_id = ?",
				LocalDateTime.now().minusSeconds(1), userId(fixture.owner()));
		search("").andExpect(jsonPath("$.totalElements").value(1));
		jdbc.update("""
				UPDATE account_sanctions SET status = 'REVOKED', revoked_at = NOW(),
				revoked_by_user_id = ?, revocation_reason = 'Test',
				expires_at = ?
				WHERE target_user_id = ?
				""", userId(viewer), LocalDateTime.now().plusHours(1), userId(fixture.owner()));
		search("").andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void sanctionedViewerCannotSearch() throws Exception {
		restrict(viewer, false);
		mockMvc.perform(viewer.owner().authorize(get("/api/team-requests")))
				.andExpect(status().isForbidden());
	}

	@Test
	void stablePaginationDoesNotDuplicateRequestsWithMultipleMatchingPositions() throws Exception {
		Fixture fixture = fixture();
		UUID first = fixture.requestId();
		UUID extra = position(fixture.owner(), fixture.teamId(), Set.of());
		UUID second = advertise(fixture.owner(), fixture.teamId(),
				Set.of(fixture.positionId(), extra));
		search("").andExpect(jsonPath("$.content[0].id").value(second.toString()));
		LocalDateTime sameTime = LocalDateTime.now().minusHours(1);
		jdbc.update("UPDATE team_requests SET created_at = ? WHERE team_id = ?",
				sameTime, fixture.teamId());
		List<String> expected = List.of(first.toString(), second.toString()).stream().sorted().toList();
		List<String> actual = new ArrayList<>();
		for (int page = 0; page < 2; page++) {
			JsonNode result = json(mockMvc.perform(viewer.owner().authorize(get("/api/team-requests"))
					.param("page", Integer.toString(page)).param("size", "1"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)));
			actual.add(result.path("content").get(0).path("id").asText());
		}
		assertThat(actual).containsExactlyElementsOf(expected);
		jdbc.update("UPDATE team_requests SET recruitment_deadline = ? WHERE id = ?",
				LocalDateTime.now().plusHours(1), second);
		mockMvc.perform(viewer.owner().authorize(get("/api/team-requests"))
				.param("sort", "DEADLINE_ASC"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(second.toString()));
		mockMvc.perform(viewer.owner().authorize(get("/api/team-requests"))
				.param("page", "2").param("size", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.totalElements").value(2));
	}

	@ParameterizedTest
	@ValueSource(strings = {"page", "size-zero", "size-large", "sort", "query", "skill",
			"many-skills"})
	void invalidSearchParametersReturnBadRequest(String invalid) throws Exception {
		var request = viewer.owner().authorize(get("/api/team-requests"));
		switch (invalid) {
			case "page" -> request.param("page", "-1");
			case "size-zero" -> request.param("size", "0");
			case "size-large" -> request.param("size", "101");
			case "sort" -> request.param("sort", "title");
			case "query" -> request.param("query", "x".repeat(201));
			case "skill" -> request.param("skillIds", "not-a-uuid");
			case "many-skills" -> {
				for (int i = 0; i < 21; i++) {
					request.param("skillIds", UUID.randomUUID().toString());
				}
			}
			default -> throw new IllegalArgumentException(invalid);
		}
		mockMvc.perform(request).andExpect(status().isBadRequest());
	}

	private ResultActions search(String query) throws Exception {
		return mockMvc.perform(viewer.owner().authorize(get("/api/team-requests"))
				.param("query", query)).andExpect(status().isOk());
	}

	private ResultActions searchSkills(UUID... skills) throws Exception {
		var request = viewer.owner().authorize(get("/api/team-requests"));
		for (UUID skill : skills) {
			request.param("skillIds", skill.toString());
		}
		return mockMvc.perform(request).andExpect(status().isOk());
	}

	private JsonNode json(ResultActions result) throws Exception {
		return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private Fixture fixture() throws Exception {
		TestProfile owner = profile();
		UUID team = teamService.createForUser(userId(owner), new CreateTeamRequest(
				"Team", null, "Build a project", "Founder role")).id();
		UUID position = position(owner, team, Set.of());
		return new Fixture(owner, team, position, advertise(owner, team, Set.of(position)));
	}

	private UUID position(TestProfile owner, UUID teamId, Set<UUID> skills) {
		return positionService.createForUser(userId(owner), teamId,
				new CreateTeamPositionRequest("Developer", null, 1, skills)).id();
	}

	private UUID advertise(TestProfile owner, UUID teamId, Set<UUID> positions) {
		return requestService.createForUser(userId(owner), teamId,
				new CreateRecruitmentRequest("Recruitment", null,
						LocalDateTime.now().plusDays(2), positions)).id();
	}

	private void join(Fixture fixture, TestProfile candidate, UUID positionId) {
		UUID invitation = invitationService.send(userId(fixture.owner()), fixture.teamId(),
				profileId(candidate), positionId).id();
		invitationService.accept(userId(candidate), invitation);
	}

	private TestProfile profile() throws Exception {
		TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper)
				.createProfile("User");
		jdbc.update("UPDATE profiles SET status = 'ACTIVE' WHERE id = ?", profileId(profile));
		return profile;
	}

	private UUID skill(String name) {
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO skills (id, name, created_at) VALUES (?, ?, NOW())",
				id, name + id);
		return id;
	}

	private void restrict(TestProfile profile, boolean permanent) {
		AccountSanction sanction = new AccountSanction();
		sanction.setTargetUserId(userId(profile));
		sanction.setTargetProfileId(profileId(profile));
		sanction.setType(permanent ? AccountSanctionType.PERMANENT_BAN
				: AccountSanctionType.TEMPORARY_SUSPENSION);
		sanction.setReason("Test");
		sanction.setExpiresAt(permanent ? null : LocalDateTime.now().plusHours(1));
		sanction.setCreatedByUserId(userId(viewer));
		sanctionRepository.saveAndFlush(sanction);
	}

	private UUID userId(TestProfile profile) {
		return UUID.fromString(profile.owner().userId());
	}

	private UUID profileId(TestProfile profile) {
		return UUID.fromString(profile.id());
	}

	private record Fixture(
			TestProfile owner, UUID teamId, UUID positionId, UUID requestId
	) {
	}
}
