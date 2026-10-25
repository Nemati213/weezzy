package ru.itmo.nemat.weezzy.team.recommendation;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.common.exception.ForbiddenException;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanction;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionRepository;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.support.TeamPositionTestData;
import ru.itmo.nemat.weezzy.team.TeamService;
import ru.itmo.nemat.weezzy.team.dto.CreateTeamRequest;
import ru.itmo.nemat.weezzy.team.dto.TeamMemberResponse;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationService;
import ru.itmo.nemat.weezzy.team.request.TeamRequestService;
import ru.itmo.nemat.weezzy.team.request.dto.CreateRecruitmentRequest;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ru.itmo.nemat.weezzy.support.ProfilePhotoTestData.insertReadyPhoto;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false", "app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false"
})
class TeamRecommendationServiceTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));

	@Autowired
	private TeamRecommendationService recommendations;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private TeamService teams;
	@Autowired
	private TeamRequestService requests;
	@Autowired
	private TeamInvitationService invitations;
	@Autowired
	private ProfileBlockService blocks;
	@Autowired
	private AccountSanctionRepository sanctions;
	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private TestProfile viewer;
	private LocalDateTime now;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void setUp() throws Exception {
		viewer = profile();
		now = LocalDateTime.now().withNano(0);
	}

	@Test
	void combinesPublicationsRanksOptionsAndPreservesRequestedTeamOrder() throws Exception {
		Fixture first = fixture();
		Fixture second = fixture();
		UUID java = skill();
		UUID spring = skill();
		UUID docker = skill();
		profileSkill(java);
		profileSkill(spring);
		positionSkill(first.positionId(), java);
		positionSkill(first.positionId(), spring);
		positionSkill(first.positionId(), docker);
		UUID otherPosition = position(first.teamId(), 1);
		positionSkill(otherPosition, java);
		UUID noSkills = position(first.teamId(), 1);
		advertise(first, Set.of(first.positionId(), otherPosition, noSkills), now.plusDays(1));

		var result = recommendations.findForUser(userId(viewer),
				List.of(second.teamId(), first.teamId(), second.teamId(), UUID.randomUUID()), now);

		assertThat(result).extracting(recommendation -> recommendation.team().id())
				.containsExactly(second.teamId(), first.teamId());
		var recommendation = result.getLast();
		assertThat(recommendation.recommendedTeamPositionId()).isEqualTo(first.positionId());
		assertThat(recommendation.options()).extracting(option -> option.position().teamPositionId())
				.containsExactly(first.positionId(), otherPosition, noSkills);
		assertThat(recommendation.options().getFirst().matchedSkillIds())
				.containsExactlyElementsOf(List.of(java, spring).stream()
						.sorted(Comparator.comparing(UUID::toString)).toList());
		assertThat(recommendation.options().getFirst().unmatchedSkillIds()).containsExactly(docker);
		assertThat(recommendation.options().getLast().matchedSkillIds()).isEmpty();
		assertThat(recommendation.team().purpose()).isEqualTo("Build a project");
	}

	@Test
	void choosesNearestDeadlineAndFallsBackAfterClosure() throws Exception {
		Fixture fixture = fixture();
		UUID closer = advertise(fixture, Set.of(fixture.positionId()), now.plusHours(1));

		var option = recommendation(fixture).options().getFirst();
		assertThat(option.request().id()).isEqualTo(closer);
		assertThat(option.position().id()).isEqualTo(linkId(closer, fixture.positionId()));
		assertThat(option.position().teamPositionId()).isEqualTo(fixture.positionId());
		assertThat(option.request().title()).isEqualTo("Recruitment " + closer);
		requests.closeForUser(userId(fixture.owner()), closer);

		var fallback = recommendation(fixture).options().getFirst();
		assertThat(fallback.request().id()).isEqualTo(fixture.requestId());
		assertThat(fallback.position().id()).isEqualTo(linkId(fixture.requestId(), fixture.positionId()));
	}

	@Test
	void publicationTiesUseNewestCreationThenPostgresUuidOrder() throws Exception {
		Fixture fixture = fixture();
		UUID other = advertise(fixture, Set.of(fixture.positionId()), now.plusDays(2));
		jdbc.update("UPDATE team_requests SET created_at = ? WHERE id = ?",
				now.minusDays(2), fixture.requestId());
		jdbc.update("UPDATE team_requests SET created_at = ? WHERE id = ?", now.minusDays(1), other);
		assertThat(recommendation(fixture).options().getFirst().request().id()).isEqualTo(other);
		jdbc.update("UPDATE team_requests SET created_at = ? WHERE team_id = ?",
				now.minusDays(1), fixture.teamId());
		UUID expected = List.of(fixture.requestId(), other).stream()
				.min(Comparator.comparing(UUID::toString)).orElseThrow();
		assertThat(recommendation(fixture).options().getFirst().request().id()).isEqualTo(expected);
	}

	@Test
	void profileChangesUpdateMainPositionAndExplanations() throws Exception {
		Fixture fixture = fixture();
		UUID java = skill();
		UUID design = skill();
		positionSkill(fixture.positionId(), java);
		UUID designer = position(fixture.teamId(), 1);
		positionSkill(designer, design);
		advertise(fixture, Set.of(designer), now.plusDays(1));
		profileSkill(java);
		assertThat(recommendation(fixture).recommendedTeamPositionId()).isEqualTo(fixture.positionId());
		jdbc.update("DELETE FROM profile_skills WHERE profile_id = ?", profileId(viewer));
		profileSkill(design);
		var updated = recommendation(fixture);
		assertThat(updated.recommendedTeamPositionId()).isEqualTo(designer);
		assertThat(updated.options().getFirst().matchedSkillIds()).containsExactly(design);
		assertThat(updated.options().getLast().unmatchedSkillIds()).containsExactly(java);
	}

	@ParameterizedTest
	@ValueSource(strings = {"full", "pending", "declined", "closed", "expired",
			"archived", "joined", "blocked", "sanctioned-owner", "inactive-owner"})
	void rechecksAvailabilityForPreviouslyKnownTeamIds(String reason) throws Exception {
		Fixture fixture = fixture();
		assertThat(recommendation(fixture).team().id()).isEqualTo(fixture.teamId());
		switch (reason) {
			case "full" -> join(fixture, profile(), fixture.positionId());
			case "pending", "declined" -> application(fixture, reason.equals("pending"));
			case "closed" -> requests.closeForUser(userId(fixture.owner()), fixture.requestId());
			case "expired" -> jdbc.update("UPDATE team_requests SET recruitment_deadline = ? WHERE id = ?",
					now, fixture.requestId());
			case "archived" -> teams.archiveForUser(userId(fixture.owner()), fixture.teamId());
			case "joined" -> join(fixture, viewer, fixture.positionId());
			case "blocked" -> blocks.block(profileId(viewer), profileId(fixture.owner()));
			case "sanctioned-owner" -> restrict(fixture.owner());
			case "inactive-owner" -> jdbc.update("UPDATE profiles SET status = 'DRAFT' WHERE id = ?",
					profileId(fixture.owner()));
			default -> throw new IllegalArgumentException(reason);
		}

		assertThat(recommendations.findForUser(userId(viewer), List.of(fixture.teamId()), now)).isEmpty();
	}

	@Test
	void pendingOnAnotherPublicationHidesOnlyThatPosition() throws Exception {
		Fixture fixture = fixture();
		UUID other = position(fixture.teamId(), 1);
		UUID request = advertise(fixture, Set.of(fixture.positionId(), other), now.plusDays(1));
		application(fixture, true);
		requests.closeForUser(userId(fixture.owner()), fixture.requestId());

		var result = recommendation(fixture);
		assertThat(result.recommendedTeamPositionId()).isEqualTo(other);
		assertThat(result.options()).extracting(option -> option.position().id())
				.containsExactly(linkId(request, other));
	}

	@ParameterizedTest
	@ValueSource(strings = {"block-out", "block-in", "draft", "deleted", "sanction"})
	void filtersMembersLikeOrdinaryTeamCardsButCountsAllOccupiedPlaces(String reason) throws Exception {
		Fixture fixture = fixture();
		jdbc.update("UPDATE team_positions SET capacity = 2 WHERE id = ?", fixture.positionId());
		TestProfile hidden = profile();
		join(fixture, hidden, fixture.positionId());
		switch (reason) {
			case "block-out" -> blocks.block(profileId(viewer), profileId(hidden));
			case "block-in" -> blocks.block(profileId(hidden), profileId(viewer));
			case "draft" -> jdbc.update("UPDATE profiles SET status = 'DRAFT' WHERE id = ?", profileId(hidden));
			case "deleted" -> jdbc.update("UPDATE profiles SET user_id = NULL WHERE id = ?", profileId(hidden));
			case "sanction" -> restrict(hidden);
			default -> throw new IllegalArgumentException(reason);
		}

		var result = recommendation(fixture);
		assertThat(result.team().members()).extracting(TeamMemberResponse::profileId)
				.containsExactly(profileId(fixture.owner()));
		assertThat(teams.findByIdForUser(userId(viewer), fixture.teamId()).members())
				.extracting(TeamMemberResponse::profileId)
				.containsExactlyElementsOf(result.team().members().stream()
						.map(TeamMemberResponse::profileId).toList());
		assertThat(result.options().getFirst().position().filledSlotsCount()).isEqualTo(1);
		assertThat(result.options().getFirst().position().remainingSlotsCount()).isEqualTo(1);
	}

	@Test
	void membersHaveProfileLinksPositionsAndReadyPhotosWithoutContacts() throws Exception {
		Fixture fixture = fixture();
		UUID readyPhoto = insertReadyPhoto(jdbc, profileId(fixture.owner()));
		UUID pendingPhoto = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO profile_photos
				(id, profile_id, object_key, content_type, size_bytes, position, is_avatar, status)
				VALUES (?, ?, ?, 'image/jpeg', 100, 1, FALSE, 'PENDING')
				""", pendingPhoto, profileId(fixture.owner()), "pending/" + pendingPhoto);

		var result = recommendation(fixture);
		var member = result.team().members().getFirst();
		assertThat(member.profileId()).isEqualTo(profileId(fixture.owner()));
		assertThat(member.displayName()).isEqualTo("User");
		assertThat(member.positionName()).isEqualTo("Founder");
		assertThat(member.positionId()).isNotNull();
		assertThat(member.photos()).hasSize(1);
		assertThat(member.photos().getFirst().id()).isEqualTo(readyPhoto);
		assertThat(member.photos().getFirst().downloadUrl()).isNotBlank();
		assertThat(objectMapper.writeValueAsString(result)).doesNotContain(
				"\"email\"", "\"telegram\"", "\"userId\"", "@authenticated_test",
				userId(fixture.owner()).toString());
	}

	@Test
	void missingProfileAndEmptySkillsStillProduceRecommendations() throws Exception {
		Fixture fixture = fixture();
		positionSkill(fixture.positionId(), skill());
		AuthenticatedTestUser noProfile = AuthenticatedTestUser.register(mockMvc, objectMapper);
		var result = recommendations.findForUser(UUID.fromString(noProfile.userId()), List.of(fixture.teamId()), now);
		assertThat(result).hasSize(1);
		assertThat(result.getFirst().options().getFirst().matchedSkillIds()).isEmpty();
		assertThat(result.getFirst().options().getFirst().unmatchedSkillIds()).hasSize(1);
		assertThat(recommendation(fixture).options().getFirst().matchedSkillIds()).isEmpty();
	}

	@Test
	void rejectsSanctionedViewerEvenForAnEmptyBatch() {
		restrict(viewer);
		assertThatThrownBy(() -> recommendations.findForUser(userId(viewer), List.of(), now))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void emptyBatchAndInputLimitsAreExplicit() {
		assertThat(recommendations.findForUser(userId(viewer), List.of(), now)).isEmpty();
		assertThatThrownBy(() -> recommendations.findForUser(userId(viewer),
				Collections.nCopies(51, UUID.randomUUID()), now))
				.isInstanceOf(ConstraintViolationException.class);
	}

	@Test
	void queryCountDoesNotGrowPerTeamPositionSkillOrMember() throws Exception {
		Fixture first = fixture();
		Fixture second = fixture();
		UUID skill = skill();
		profileSkill(skill);
		UUID extra = position(second.teamId(), 4);
		positionSkill(extra, skill);
		advertise(second, Set.of(extra), now.plusDays(1));
		for (int i = 0; i < 3; i++) {
			TestProfile member = profile();
			join(second, member, extra);
			insertReadyPhoto(jdbc, profileId(member));
		}
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		try {
			statistics.clear();
			assertThat(recommendations.findForUser(userId(viewer), List.of(first.teamId()), now)).hasSize(1);
			long single = statistics.getPrepareStatementCount();
			statistics.clear();
			assertThat(recommendations.findForUser(userId(viewer), List.of(first.teamId(), second.teamId()), now))
					.hasSize(2);
			assertThat(statistics.getPrepareStatementCount()).isEqualTo(single);
			assertThat(single).isLessThanOrEqualTo(15);
		} finally {
			statistics.setStatisticsEnabled(false);
		}
	}

	private TeamRecommendation recommendation(Fixture fixture) {
		return recommendations.findForUser(userId(viewer), List.of(fixture.teamId()), now).getFirst();
	}

	private Fixture fixture() throws Exception {
		TestProfile owner = profile();
		UUID teamId = teams.createForUser(userId(owner), new CreateTeamRequest(
				"Team", "Description", "Build a project", "Founder")).id();
		UUID positionId = position(teamId, 1);
		Fixture fixture = new Fixture(owner, teamId, positionId, null);
		return new Fixture(owner, teamId, positionId,
				advertise(fixture, Set.of(positionId), now.plusDays(2)));
	}

	private UUID position(UUID teamId, int capacity) {
		return TeamPositionTestData.createPosition(jdbc, teamId, "Developer", capacity);
	}

	private UUID advertise(Fixture fixture, Set<UUID> positions, LocalDateTime deadline) {
		UUID id = requests.createForUser(userId(fixture.owner()), fixture.teamId(),
				new CreateRecruitmentRequest("Recruitment", "Description", deadline, positions)).id();
		jdbc.update("UPDATE team_requests SET title = ? WHERE id = ?", "Recruitment " + id, id);
		return id;
	}

	private UUID linkId(UUID requestId, UUID positionId) {
		return jdbc.queryForObject("""
				SELECT id FROM team_request_positions WHERE request_id = ? AND team_position_id = ?
				""", UUID.class, requestId, positionId);
	}

	private UUID skill() {
		UUID id = UUID.randomUUID();
		jdbc.update("INSERT INTO skills (id, name, created_at) VALUES (?, ?, ?)", id, "Skill " + id, now);
		return id;
	}

	private void positionSkill(UUID positionId, UUID skillId) {
		jdbc.update("INSERT INTO team_position_skills (position_id, skill_id) VALUES (?, ?)",
				positionId, skillId);
	}

	private void profileSkill(UUID skillId) {
		jdbc.update("INSERT INTO profile_skills (profile_id, skill_id, created_at) VALUES (?, ?, ?)",
				profileId(viewer), skillId, now);
	}

	private void application(Fixture fixture, boolean pending) {
		jdbc.update("""
				INSERT INTO team_applications
				(id, position_id, applicant_profile_id, status, created_at, resolved_at)
				VALUES (?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), linkId(fixture.requestId(), fixture.positionId()),
				profileId(viewer), pending ? "PENDING" : "DECLINED", now.minusDays(2),
				pending ? null : now.minusHours(1));
	}

	private void join(Fixture fixture, TestProfile member, UUID positionId) {
		UUID invitationId = invitations.send(userId(fixture.owner()), fixture.teamId(),
				profileId(member), positionId).id();
		invitations.accept(userId(member), invitationId);
	}

	private TestProfile profile() throws Exception {
		TestProfile result = AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile("User");
		jdbc.update("UPDATE profiles SET status = 'ACTIVE' WHERE id = ?", profileId(result));
		return result;
	}

	private void restrict(TestProfile profile) {
		AccountSanction sanction = new AccountSanction();
		sanction.setTargetUserId(userId(profile));
		sanction.setTargetProfileId(profileId(profile));
		sanction.setType(AccountSanctionType.PERMANENT_BAN);
		sanction.setReason("Test");
		sanction.setCreatedByUserId(userId(viewer));
		sanctions.saveAndFlush(sanction);
	}

	private UUID userId(TestProfile profile) {
		return UUID.fromString(profile.owner().userId());
	}

	private UUID profileId(TestProfile profile) {
		return UUID.fromString(profile.id());
	}

	private record Fixture(TestProfile owner, UUID teamId, UUID positionId, UUID requestId) {
	}
}
