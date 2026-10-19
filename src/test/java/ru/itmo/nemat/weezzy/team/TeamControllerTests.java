package ru.itmo.nemat.weezzy.team;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanction;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionRepository;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.team.dto.CreateTeamRequest;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitation;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationRepository;
import ru.itmo.nemat.weezzy.team.member.TeamMember;
import ru.itmo.nemat.weezzy.team.position.TeamPositionRepository;
import static ru.itmo.nemat.weezzy.support.TeamPositionTestData.positionId;
import ru.itmo.nemat.weezzy.team.member.TeamMemberId;
import ru.itmo.nemat.weezzy.team.member.TeamMemberRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.itmo.nemat.weezzy.support.ProfilePhotoTestData.insertReadyPhoto;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false",
		"app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false"
})
class TeamControllerTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

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
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TeamRepository teamRepository;
	@Autowired
	private TeamPositionRepository teamPositionRepository;

	@MockitoSpyBean
	private TeamMemberRepository memberRepository;

	@Autowired
	private TeamInvitationRepository invitationRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private AccountSanctionRepository sanctionRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void creatorBecomesOwnerAndOnlyMemberWithoutExposingContacts() throws Exception {
		TestProfile owner = activeProfile("Owner");
		UUID photoId = insertReadyPhoto(jdbcTemplate, UUID.fromString(owner.id()));
		String response = create(owner.owner(), "  Hackathon team  ", "  Description  ")
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.name").value("Hackathon team"))
				.andExpect(jsonPath("$.description").value("Description"))
				.andExpect(jsonPath("$.ownerProfileId").value(owner.id()))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty())
				.andExpect(jsonPath("$.members.length()").value(1))
				.andExpect(jsonPath("$.members[0].profileId").value(owner.id()))
				.andExpect(jsonPath("$.members[0].displayName").value("Owner"))
				.andExpect(jsonPath("$.members[0].joinedAt").isNotEmpty())
				.andExpect(jsonPath("$.members[0].photos[0].id").value(photoId.toString()))
				.andExpect(jsonPath("$.members[0].photos[0].downloadUrl").isNotEmpty())
				.andExpect(jsonPath("$.members[0].telegram").doesNotExist())
				.andExpect(jsonPath("$.members[0].email").doesNotExist())
				.andExpect(jsonPath("$.members[0].userId").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		UUID teamId = UUID.fromString(objectMapper.readTree(response).path("id").asText());

		assertThat(teamRepository.findById(teamId).orElseThrow().getOwnerProfile().getId())
				.isEqualTo(UUID.fromString(owner.id()));
		assertThat(memberRepository.countByTeamId(teamId)).isEqualTo(1);
		assertThat(memberRepository.existsById(new TeamMemberId(
				teamId, UUID.fromString(owner.id())
		))).isTrue();
		mockMvc.perform(owner.owner().authorize(get("/api/teams/" + teamId)))
				.andExpect(status().isOk());
	}

	@Test
	void memberSaveFailureRollsBackTeamCreation() throws Exception {
		TestProfile owner = activeProfile("Owner");
		long teamCount = teamRepository.count();
		long memberCount = memberRepository.count();
		doThrow(new DataIntegrityViolationException("Test member save failure"))
				.when(memberRepository).saveAndFlush(any(TeamMember.class));

		assertThatThrownBy(() -> create(owner.owner(), "Team", null))
				.hasRootCauseInstanceOf(DataIntegrityViolationException.class);

		assertThat(teamRepository.count()).isEqualTo(teamCount);
		assertThat(memberRepository.count()).isEqualTo(memberCount);
	}

	@Test
	void requestCannotAssignAnotherOwnerOrAddMembers() throws Exception {
		TestProfile owner = activeProfile("Owner");
		TestProfile other = activeProfile("Other");

		mockMvc.perform(owner.owner().authorize(post("/api/teams"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"name", "Team",
								"purpose", "Build project",
								"ownerPositionName", "Backend",
								"ownerProfileId", other.id(),
								"members", List.of(other.id()),
								"status", "ARCHIVED"
						))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerProfileId").value(owner.id()))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.members.length()").value(1))
				.andExpect(jsonPath("$.members[0].profileId").value(owner.id()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	void blankDescriptionIsNormalizedToNull(String description) throws Exception {
		TestProfile owner = activeProfile("Owner");
		create(owner.owner(), "Team", description)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.description").doesNotExist());
	}

	@ParameterizedTest
	@MethodSource("invalidRequests")
	void createValidatesRequestWithoutSavingTeam(CreateTeamRequest request) throws Exception {
		TestProfile owner = activeProfile("Owner");
		long before = teamRepository.count();

		create(owner.owner(), request.name(), request.description())
				.andExpect(status().isBadRequest());

		assertThat(teamRepository.count()).isEqualTo(before);
	}

	static Stream<CreateTeamRequest> invalidRequests() {
		return Stream.of(
				new CreateTeamRequest(null, null, "Build project", "Backend"),
				new CreateTeamRequest("", null, "Build project", "Backend"),
				new CreateTeamRequest(" \t\n ", null, "Build project", "Backend"),
				new CreateTeamRequest("a".repeat(121), null, "Build project", "Backend"),
				new CreateTeamRequest("Team", "a".repeat(2001), "Build project", "Backend")
		);
	}

	@Test
	void createAndReadRequireAuthentication() throws Exception {
		mockMvc.perform(post("/api/teams").contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Team\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/teams/" + UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void userWithoutProfileCannotCreateTeam() throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		long before = teamRepository.count();

		create(user, "Team", null).andExpect(status().isNotFound());

		assertThat(teamRepository.count()).isEqualTo(before);
	}

	@ParameterizedTest
	@EnumSource(value = ProfileStatus.class, names = {"DRAFT", "HIDDEN", "DELETED"})
	void inactiveProfileCannotCreateTeam(ProfileStatus status) throws Exception {
		TestProfile owner = activeProfile("Owner");
		setProfileStatus(owner, status);
		long before = teamRepository.count();

		create(owner.owner(), "Team", null)
				.andExpect(status().isConflict());

		assertThat(teamRepository.count()).isEqualTo(before);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void outsiderCanReadCardAndOpenMemberProfile(boolean hasProfile) throws Exception {
		TestProfile owner = activeProfile("Owner");
		UUID teamId = createTeam(owner);
		TestProfile teammate = activeProfile("Teammate");
		addMember(teamId, teammate);
		UUID photoId = insertReadyPhoto(jdbcTemplate, UUID.fromString(teammate.id()));
		AuthenticatedTestUser viewer = AuthenticatedTestUser.register(mockMvc, objectMapper);
		if (hasProfile) {
			viewer.createProfile("Viewer");
		}

		mockMvc.perform(viewer.authorize(get("/api/teams/" + teamId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members.length()").value(2))
				.andExpect(jsonPath("$.members[1].profileId").value(teammate.id()))
				.andExpect(jsonPath("$.members[1].displayName").value("Teammate"))
				.andExpect(jsonPath("$.members[1].photos[0].id").value(photoId.toString()))
				.andExpect(jsonPath("$.members[1].telegram").doesNotExist())
				.andExpect(jsonPath("$.members[1].email").doesNotExist())
				.andExpect(jsonPath("$.members[1].userId").doesNotExist());
		mockMvc.perform(viewer.authorize(get(teammate.location())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.telegram").doesNotExist());
	}

	@Test
	void pendingInviteeDoesNotAppearInMembers() throws Exception {
		TestProfile owner = activeProfile("Owner");
		UUID teamId = createTeam(owner);
		TestProfile invitee = activeProfile("Invitee");
		TeamInvitation invitation = new TeamInvitation();
		invitation.setTeam(teamRepository.findById(teamId).orElseThrow());
		invitation.setPosition(teamPositionRepository.getReferenceById(positionId(jdbcTemplate, teamId)));
		invitation.setInviterProfile(profileRepository.findById(
				UUID.fromString(owner.id())
		).orElseThrow());
		invitation.setInviteeProfile(profileRepository.findById(
				UUID.fromString(invitee.id())
		).orElseThrow());
		invitationRepository.saveAndFlush(invitation);

		mockMvc.perform(invitee.owner().authorize(get("/api/teams/" + teamId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members.length()").value(1))
				.andExpect(jsonPath("$.members[0].profileId").value(owner.id()));
	}

	@Test
	void missingAndArchivedTeamsReturnNotFound() throws Exception {
		TestProfile owner = activeProfile("Owner");
		UUID teamId = createTeam(owner);
		jdbcTemplate.update("UPDATE teams SET status = 'ARCHIVED' WHERE id = ?", teamId);

		mockMvc.perform(owner.owner().authorize(get("/api/teams/" + teamId)))
				.andExpect(status().isNotFound());
		mockMvc.perform(owner.owner().authorize(get("/api/teams/" + UUID.randomUUID())))
				.andExpect(status().isNotFound());
		mockMvc.perform(owner.owner().authorize(get("/api/teams/invalid-id")))
				.andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void ownerBlockHidesTeamInBothDirections(boolean ownerBlocks) throws Exception {
		TestProfile owner = activeProfile("Owner");
		UUID teamId = createTeam(owner);
		TestProfile viewer = activeProfile("Viewer");
		block(ownerBlocks ? owner : viewer, ownerBlocks ? viewer : owner);

		mockMvc.perform(viewer.owner().authorize(get("/api/teams/" + teamId)))
				.andExpect(status().isNotFound());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void blockedMemberIsHiddenInBothDirections(boolean memberBlocks) throws Exception {
		TestProfile owner = activeProfile("Owner");
		UUID teamId = createTeam(owner);
		TestProfile teammate = activeProfile("Teammate");
		addMember(teamId, teammate);
		insertReadyPhoto(jdbcTemplate, UUID.fromString(teammate.id()));
		TestProfile viewer = activeProfile("Viewer");
		block(memberBlocks ? teammate : viewer, memberBlocks ? viewer : teammate);

		mockMvc.perform(viewer.owner().authorize(get("/api/teams/" + teamId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members.length()").value(1))
				.andExpect(jsonPath("$.members[0].profileId").value(owner.id()));
		assertThat(memberRepository.countByTeamId(teamId)).isEqualTo(2);
	}

	@ParameterizedTest
	@EnumSource(value = ProfileStatus.class, names = {"DRAFT", "HIDDEN", "DELETED"})
	void inactiveOwnerHidesTeam(ProfileStatus status) throws Exception {
		TestProfile owner = activeProfile("Owner");
		UUID teamId = createTeam(owner);
		setProfileStatus(owner, status);
		AuthenticatedTestUser viewer = AuthenticatedTestUser.register(mockMvc, objectMapper);

		mockMvc.perform(viewer.authorize(get("/api/teams/" + teamId)))
				.andExpect(status().isNotFound());
	}

	@ParameterizedTest
	@EnumSource(value = ProfileStatus.class, names = {"DRAFT", "HIDDEN", "DELETED"})
	void inactiveMemberIsHidden(ProfileStatus status) throws Exception {
		TestProfile owner = activeProfile("Owner");
		UUID teamId = createTeam(owner);
		TestProfile teammate = activeProfile("Teammate");
		addMember(teamId, teammate);
		setProfileStatus(teammate, status);

		mockMvc.perform(owner.owner().authorize(get("/api/teams/" + teamId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members.length()").value(1));
	}

	@Test
	void sanctionedOwnerCannotUseTeamApiAndTeamIsHiddenFromOthers() throws Exception {
		TestProfile owner = activeProfile("Owner");
		UUID teamId = createTeam(owner);
		sanction(owner);
		AuthenticatedTestUser viewer = AuthenticatedTestUser.register(mockMvc, objectMapper);

		create(owner.owner(), "Other team", null).andExpect(status().isForbidden());
		mockMvc.perform(owner.owner().authorize(get("/api/teams/" + teamId)))
				.andExpect(status().isForbidden());
		mockMvc.perform(viewer.authorize(get("/api/teams/" + teamId)))
				.andExpect(status().isNotFound());
	}

	@Test
	void sanctionedMemberIsHiddenAndExpiredSanctionDoesNotHideMember() throws Exception {
		TestProfile owner = activeProfile("Owner");
		UUID teamId = createTeam(owner);
		TestProfile teammate = activeProfile("Teammate");
		addMember(teamId, teammate);
		sanction(teammate);

		mockMvc.perform(owner.owner().authorize(get("/api/teams/" + teamId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members.length()").value(1));
		jdbcTemplate.update("""
				UPDATE account_sanctions
				SET type = 'TEMPORARY_SUSPENSION', expires_at = ?
				WHERE target_user_id = ?
				""", LocalDateTime.now().minusMinutes(1), UUID.fromString(teammate.owner().userId()));

		mockMvc.perform(owner.owner().authorize(get("/api/teams/" + teamId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.members.length()").value(2));
	}

	private TestProfile activeProfile(String name) throws Exception {
		TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper)
				.createProfile(name);
		setProfileStatus(profile, ProfileStatus.ACTIVE);
		return profile;
	}

	private void setProfileStatus(TestProfile profile, ProfileStatus status) {
		jdbcTemplate.update("UPDATE profiles SET status = ? WHERE id = ?",
				status.name(), UUID.fromString(profile.id()));
	}

	private ResultActions create(
			AuthenticatedTestUser user,
			String name,
			String description
	) throws Exception {
		return mockMvc.perform(user.authorize(post("/api/teams"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CreateTeamRequest(name, description, "Build project", "Backend"))));
	}

	private UUID createTeam(TestProfile owner) throws Exception {
		ResultActions result = create(owner.owner(), "Team", null)
				.andExpect(status().isCreated());
		String response = result.andReturn().getResponse().getContentAsString();
		UUID teamId = UUID.fromString(objectMapper.readTree(response).path("id").asText());
		result.andExpect(header().string("Location", "/api/teams/" + teamId));
		return teamId;
	}

	private void addMember(UUID teamId, TestProfile profile) {
		TeamMember member = new TeamMember();
		member.setId(new TeamMemberId(teamId, UUID.fromString(profile.id())));
		member.setTeam(teamRepository.findById(teamId).orElseThrow());
		member.setProfile(profileRepository.findById(UUID.fromString(profile.id())).orElseThrow());
		member.setPosition(teamPositionRepository.getReferenceById(positionId(jdbcTemplate, teamId)));
		memberRepository.saveAndFlush(member);
	}

	private void block(TestProfile blocker, TestProfile blocked) throws Exception {
		mockMvc.perform(blocker.owner().authorize(post("/api/blocks/" + blocked.id())))
				.andExpect(status().isOk());
	}

	private void sanction(TestProfile profile) {
		AccountSanction sanction = new AccountSanction();
		sanction.setTargetUserId(UUID.fromString(profile.owner().userId()));
		sanction.setTargetProfileId(UUID.fromString(profile.id()));
		sanction.setType(AccountSanctionType.PERMANENT_BAN);
		sanction.setReason("Test sanction");
		sanction.setCreatedByUserId(UUID.randomUUID());
		sanctionRepository.saveAndFlush(sanction);
	}
}
