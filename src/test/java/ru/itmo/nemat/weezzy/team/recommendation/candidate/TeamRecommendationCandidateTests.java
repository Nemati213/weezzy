package ru.itmo.nemat.weezzy.team.recommendation.candidate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
import ru.itmo.nemat.weezzy.support.TeamPositionTestData;
import ru.itmo.nemat.weezzy.team.TeamService;
import ru.itmo.nemat.weezzy.team.application.TeamApplicationProperties;
import ru.itmo.nemat.weezzy.team.dto.CreateTeamRequest;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationService;
import ru.itmo.nemat.weezzy.team.request.TeamRequestService;
import ru.itmo.nemat.weezzy.team.request.dto.CreateRecruitmentRequest;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false", "app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false"
})
class TeamRecommendationCandidateTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));

	@Autowired
	private TeamRecommendationCandidateRepository candidateRepository;
	@Autowired
	private TeamApplicationProperties applicationProperties;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private TeamService teamService;
	@Autowired
	private TeamRequestService requestService;
	@Autowired
	private TeamInvitationService invitationService;
	@Autowired
	private ProfileBlockService blockService;
	@Autowired
	private AccountSanctionRepository sanctionRepository;

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
		jdbc.update("DELETE FROM team_requests");
		viewer = profile();
		now = LocalDateTime.now().withNano(0);
	}

	@Test
	void returnsCompactMetadataWithoutRequiringMatchingSkills() throws Exception {
		Fixture fixture = fixture();
		LocalDateTime positionCreatedAt = now.minusDays(2);
		LocalDateTime requestCreatedAt = now.minusDays(1);
		jdbc.update("UPDATE team_positions SET created_at = ? WHERE id = ?",
				positionCreatedAt, fixture.positionId());
		jdbc.update("UPDATE team_requests SET created_at = ? WHERE id = ?",
				requestCreatedAt, fixture.requestId());

		assertThat(find()).containsExactly(new TeamRecommendationCandidate(
				fixture.teamId(), fixture.requestId(), requestCreatedAt, now.plusDays(2),
				fixture.linkId(), fixture.positionId(), positionCreatedAt));
		addSkill(fixture.positionId());
		addSkill(fixture.positionId());
		assertThat(find()).extracting(TeamRecommendationCandidate::requestPositionId)
				.containsExactly(fixture.linkId());
	}

	@ParameterizedTest
	@ValueSource(strings = {"closed", "expired", "deadline-now", "archived",
			"draft-owner", "deleted-owner", "missing-owner", "ban", "suspension",
			"block-out", "block-in", "full"})
	void filtersUnavailableCandidatesBeforeLimitingTheBatch(String reason) throws Exception {
		Fixture hidden = fixture();
		Fixture visible = fixture();
		jdbc.update("UPDATE team_request_positions SET id = ? WHERE id = ?",
				new UUID(0, 1), hidden.linkId());
		jdbc.update("UPDATE team_request_positions SET id = ? WHERE id = ?",
				new UUID(-1, -1), visible.linkId());
		switch (reason) {
			case "closed" -> requestService.closeForUser(userId(hidden.owner()), hidden.requestId());
			case "expired" -> deadline(hidden.requestId(), now.minusSeconds(1));
			case "deadline-now" -> deadline(hidden.requestId(), now);
			case "archived" -> teamService.archiveForUser(userId(hidden.owner()), hidden.teamId());
			case "draft-owner" -> jdbc.update("UPDATE profiles SET status = 'DRAFT' WHERE id = ?",
					profileId(hidden.owner()));
			case "deleted-owner" -> jdbc.update("UPDATE profiles SET user_id = NULL WHERE id = ?",
					profileId(hidden.owner()));
			case "missing-owner" -> jdbc.update(
					"DELETE FROM team_members WHERE team_id = ? AND profile_id = ?",
					hidden.teamId(), profileId(hidden.owner()));
			case "ban", "suspension" -> restrict(hidden.owner(), reason.equals("ban"));
			case "block-out" -> blockService.block(profileId(viewer), profileId(hidden.owner()));
			case "block-in" -> blockService.block(profileId(hidden.owner()), profileId(viewer));
			case "full" -> join(hidden, profile());
			default -> throw new IllegalArgumentException(reason);
		}

		assertThat(candidates(profileId(viewer), null, 1))
				.extracting(TeamRecommendationCandidate::teamId)
				.containsExactly(visible.teamId());
	}

	@Test
	void expiredAndRevokedOwnerSanctionsDoNotExcludeCandidates() throws Exception {
		Fixture expired = fixture();
		Fixture revoked = fixture();
		UUID expiredSanction = restrict(expired.owner(), false);
		UUID revokedSanction = restrict(revoked.owner(), true);
		jdbc.update("UPDATE account_sanctions SET expires_at = ? WHERE id = ?",
				now, expiredSanction);
		jdbc.update("""
				UPDATE account_sanctions SET status = 'REVOKED', revoked_at = ?,
				revoked_by_user_id = ?, revocation_reason = 'Test' WHERE id = ?
				""", now, userId(viewer), revokedSanction);

		assertThat(find()).extracting(TeamRecommendationCandidate::teamId)
				.containsExactlyInAnyOrder(expired.teamId(), revoked.teamId());
	}

	@Test
	void excludesBothOwnedAndJoinedTeams() throws Exception {
		Fixture own = fixture(viewer);
		Fixture joined = fixture();
		Fixture available = fixture();
		join(joined, viewer);
		jdbc.update("UPDATE team_positions SET capacity = 2 WHERE id = ?", joined.positionId());

		assertThat(find()).extracting(TeamRecommendationCandidate::teamId)
				.containsExactly(available.teamId());
		assertThat(own.teamId()).isNotEqualTo(joined.teamId());
	}

	@Test
	void accountWithoutProfileAndInactiveProfileCanBrowse() throws Exception {
		Fixture fixture = fixture();
		assertThat(candidates(null, null, 20))
				.extracting(TeamRecommendationCandidate::teamId)
				.containsExactly(fixture.teamId());
		jdbc.update("UPDATE profiles SET status = 'DRAFT' WHERE id = ?", profileId(viewer));
		assertThat(find()).extracting(TeamRecommendationCandidate::teamId)
				.containsExactly(fixture.teamId());
	}

	@Test
	void onlyFreePositionsAreSelectedAndPendingInvitationsDoNotReserveThem() throws Exception {
		Fixture fixture = fixture();
		UUID free = TeamPositionTestData.createPosition(jdbc, fixture.teamId(), "Free", 1);
		UUID request = advertise(fixture.owner(), fixture.teamId(), Set.of(fixture.positionId(), free));
		UUID freeLink = linkId(request, free);
		join(fixture, profile());
		invitationService.send(userId(fixture.owner()), fixture.teamId(),
				profileId(viewer), free);

		assertThat(find()).extracting(TeamRecommendationCandidate::requestPositionId)
				.containsExactly(freeLink);
	}

	@ParameterizedTest
	@ValueSource(strings = {"PENDING", "DECLINED"})
	void applicationOnClosedPublicationExcludesTheSamePositionAcrossAnnouncements(
			String status
	) throws Exception {
		Fixture fixture = fixture();
		UUID otherPosition = TeamPositionTestData.createPosition(jdbc, fixture.teamId(), "Other", 1);
		UUID otherRequest = advertise(fixture.owner(), fixture.teamId(),
				Set.of(fixture.positionId(), otherPosition));
		application(fixture.linkId(), viewer, status,
				status.equals("PENDING") ? null : now.minusHours(1));
		requestService.closeForUser(userId(fixture.owner()), fixture.requestId());

		assertThat(find()).extracting(TeamRecommendationCandidate::requestPositionId)
				.containsExactly(linkId(otherRequest, otherPosition));
	}

	@Test
	void applicationsOnOtherPositionsOrByOtherPeopleDoNotExcludeThePosition() throws Exception {
		Fixture fixture = fixture();
		UUID other = TeamPositionTestData.createPosition(jdbc, fixture.teamId(), "Other", 1);
		UUID otherRequest = advertise(fixture.owner(), fixture.teamId(), Set.of(other));
		application(linkId(otherRequest, other), viewer, "PENDING", null);
		TestProfile somebodyElse = profile();
		application(fixture.linkId(), somebodyElse, "PENDING", null);
		application(fixture.linkId(), somebodyElse, "DECLINED", now.minusMinutes(1));

		assertThat(find()).extracting(TeamRecommendationCandidate::requestPositionId)
				.containsExactly(fixture.linkId());
	}

	@ParameterizedTest
	@CsvSource({"86399, false", "86400, true", "86401, true"})
	void declineCooldownEndsExactlyAtTwentyFourHours(long secondsAgo, boolean available)
			throws Exception {
		Fixture fixture = fixture();
		application(fixture.linkId(), viewer, "DECLINED", now.minusSeconds(secondsAgo));

		assertThat(find()).hasSize(available ? 1 : 0);
	}

	@Test
	void olderDecisionAndWithdrawalDoNotCancelARecentDecline() throws Exception {
		Fixture fixture = fixture();
		application(fixture.linkId(), viewer, "DECLINED", now.minusDays(3));
		application(fixture.linkId(), viewer, "DECLINED", now.minusHours(1));
		application(fixture.linkId(), viewer, "WITHDRAWN", now.minusMinutes(1));

		assertThat(find()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"WITHDRAWN", "ACCEPTED"})
	void resolvedApplicationDoesNotPreventRecommendationsAfterMembershipEnds(String status)
			throws Exception {
		Fixture fixture = fixture();
		application(fixture.linkId(), viewer, status, now.minusMinutes(1));
		if (status.equals("ACCEPTED")) {
			join(fixture, viewer);
			teamService.leaveForUser(userId(viewer), fixture.teamId());
		}

		assertThat(find()).extracting(TeamRecommendationCandidate::requestPositionId)
				.containsExactly(fixture.linkId());
	}

	@Test
	void freedPositionReturnsWithoutChangingItsIdentity() throws Exception {
		Fixture fixture = fixture();
		TestProfile member = profile();
		join(fixture, member);
		assertThat(find()).isEmpty();
		teamService.leaveForUser(userId(member), fixture.teamId());
		assertThat(find()).extracting(TeamRecommendationCandidate::requestPositionId)
				.containsExactly(fixture.linkId());
	}

	@Test
	void batchesKeepAllPublicationsWithoutDuplicatesFromSkillsOrHistory() throws Exception {
		Fixture fixture = fixture();
		UUID otherPosition = TeamPositionTestData.createPosition(jdbc, fixture.teamId(), "Other", 1);
		UUID second = advertise(fixture.owner(), fixture.teamId(),
				Set.of(fixture.positionId(), otherPosition));
		addSkill(fixture.positionId());
		addSkill(fixture.positionId());
		application(fixture.linkId(), viewer, "WITHDRAWN", now.minusHours(1));
		application(fixture.linkId(), viewer, "DECLINED", now.minusDays(2));
		List<UUID> expected = List.of(fixture.linkId(), linkId(second, fixture.positionId()),
				linkId(second, otherPosition)).stream()
				.sorted(Comparator.comparing(UUID::toString)).toList();
		List<TeamRecommendationCandidate> actual = new ArrayList<>();
		UUID after = null;
		for (UUID expectedId : expected) {
			var batch = candidates(profileId(viewer), after, 1);
			assertThat(batch).extracting(TeamRecommendationCandidate::requestPositionId)
					.containsExactly(expectedId);
			actual.addAll(batch);
			after = expectedId;
		}

		assertThat(candidates(profileId(viewer), after, 1)).isEmpty();
		assertThat(actual).filteredOn(row -> row.teamPositionId().equals(fixture.positionId()))
				.hasSize(2);
		assertThat(actual).extracting(TeamRecommendationCandidate::requestId)
				.containsOnly(fixture.requestId(), second);
	}

	@Test
	void continuationDoesNotDependOnTheBoundaryRowStillExisting() throws Exception {
		fixture();
		fixture();
		var first = candidates(profileId(viewer), null, 1).getFirst();
		var expected = candidates(profileId(viewer), first.requestPositionId(), 1);
		jdbc.update("DELETE FROM team_request_positions WHERE id = ?", first.requestPositionId());

		assertThat(candidates(profileId(viewer), first.requestPositionId(), 1))
				.isEqualTo(expected).hasSize(1);
	}

	private List<TeamRecommendationCandidate> find() {
		return candidates(profileId(viewer), null, 500);
	}

	private List<TeamRecommendationCandidate> candidates(UUID viewerProfileId, UUID after, int size) {
		return candidateRepository.findEligible(viewerProfileId, now,
				now.minus(applicationProperties.reapplicationCooldown()), after, PageRequest.of(0, size));
	}

	private Fixture fixture() throws Exception {
		return fixture(profile());
	}

	private Fixture fixture(TestProfile owner) {
		UUID teamId = teamService.createForUser(userId(owner), new CreateTeamRequest(
				"Team", null, "Build a project", "Founder")).id();
		UUID positionId = TeamPositionTestData.createPosition(jdbc, teamId, "Developer", 1);
		UUID requestId = advertise(owner, teamId, Set.of(positionId));
		return new Fixture(owner, teamId, positionId, requestId, linkId(requestId, positionId));
	}

	private UUID advertise(TestProfile owner, UUID teamId, Set<UUID> positions) {
		return requestService.createForUser(userId(owner), teamId,
				new CreateRecruitmentRequest("Recruitment", null, now.plusDays(2), positions)).id();
	}

	private UUID linkId(UUID requestId, UUID positionId) {
		return jdbc.queryForObject("""
				SELECT id FROM team_request_positions WHERE request_id = ? AND team_position_id = ?
				""", UUID.class, requestId, positionId);
	}

	private void application(UUID linkId, TestProfile applicant, String status, LocalDateTime resolvedAt) {
		jdbc.update("""
				INSERT INTO team_applications
				(id, position_id, applicant_profile_id, status, created_at, resolved_at)
				VALUES (?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), linkId, profileId(applicant), status, now.minusDays(4), resolvedAt);
	}

	private void deadline(UUID requestId, LocalDateTime deadline) {
		jdbc.update("UPDATE team_requests SET recruitment_deadline = ? WHERE id = ?",
				deadline, requestId);
	}

	private void addSkill(UUID positionId) {
		UUID skillId = UUID.randomUUID();
		jdbc.update("INSERT INTO skills (id, name, created_at) VALUES (?, ?, ?)",
				skillId, "Skill " + skillId, now);
		jdbc.update("INSERT INTO team_position_skills (position_id, skill_id) VALUES (?, ?)",
				positionId, skillId);
	}

	private void join(Fixture fixture, TestProfile member) {
		UUID invitation = invitationService.send(userId(fixture.owner()), fixture.teamId(),
				profileId(member), fixture.positionId()).id();
		invitationService.accept(userId(member), invitation);
	}

	private TestProfile profile() throws Exception {
		TestProfile result = AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile("User");
		jdbc.update("UPDATE profiles SET status = 'ACTIVE' WHERE id = ?", profileId(result));
		return result;
	}

	private UUID restrict(TestProfile profile, boolean permanent) {
		AccountSanction sanction = new AccountSanction();
		sanction.setTargetUserId(userId(profile));
		sanction.setTargetProfileId(profileId(profile));
		sanction.setType(permanent ? AccountSanctionType.PERMANENT_BAN
				: AccountSanctionType.TEMPORARY_SUSPENSION);
		sanction.setReason("Test");
		sanction.setExpiresAt(permanent ? null : now.plusHours(1));
		sanction.setCreatedByUserId(userId(viewer));
		return sanctionRepository.saveAndFlush(sanction).getId();
	}

	private UUID userId(TestProfile profile) {
		return UUID.fromString(profile.owner().userId());
	}

	private UUID profileId(TestProfile profile) {
		return UUID.fromString(profile.id());
	}

	private record Fixture(
			TestProfile owner, UUID teamId, UUID positionId, UUID requestId, UUID linkId
	) {
	}
}
