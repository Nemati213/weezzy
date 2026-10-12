package ru.itmo.nemat.weezzy.team;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitation;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationRepository;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationStatus;
import ru.itmo.nemat.weezzy.team.member.TeamMember;
import ru.itmo.nemat.weezzy.team.member.TeamMemberId;
import ru.itmo.nemat.weezzy.team.member.TeamMemberRepository;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false",
		"app.lunch.lifecycle.enabled=false"
})
class TeamRepositoryTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private TeamMemberRepository memberRepository;

	@Autowired
	private TeamInvitationRepository invitationRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void teamMembersAndInvitationsCanBeSavedSeparately() {
		Team team = createTeam();
		TeamMember owner = addMember(team, team.getOwnerProfile());
		TeamMember teammate = addMember(team, createProfile("Teammate"));
		TeamInvitation invitation = invite(team, createProfile("Invitee"));

		Team found = teamRepository.findById(team.getId()).orElseThrow();
		assertThat(found.getOwnerProfile().getId())
				.isEqualTo(team.getOwnerProfile().getId());
		assertThat(found.getName()).isEqualTo("Hackathon team");
		assertThat(found.getDescription()).isEqualTo("A service for students");
		assertThat(found.getStatus()).isEqualTo(TeamStatus.ACTIVE);
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
		assertThat(memberRepository.findByTeamIdOrderByJoinedAtAscProfileIdAsc(team.getId()))
				.extracting(member -> member.getProfile().getId())
				.containsExactly(owner.getProfile().getId(), teammate.getProfile().getId());
		assertThat(memberRepository.countByTeamId(team.getId())).isEqualTo(2);
		assertThat(memberRepository.existsById(new TeamMemberId(
				team.getId(), invitation.getInviteeProfile().getId()
		))).isFalse();

		TeamInvitation pending = invitationRepository.findById(invitation.getId())
				.orElseThrow();
		assertThat(pending.getTeam().getId()).isEqualTo(team.getId());
		assertThat(pending.getInviterProfile().getId())
				.isEqualTo(team.getOwnerProfile().getId());
		assertThat(pending.getInviteeProfile().getId())
				.isEqualTo(invitation.getInviteeProfile().getId());
		assertThat(pending.getStatus()).isEqualTo(TeamInvitationStatus.PENDING);
		assertThat(pending.getCreatedAt()).isNotNull();
		assertThat(pending.getResolvedAt()).isNull();
	}

	@Test
	void profileCannotBeAddedTwiceToSameTeam() {
		Team team = createTeam();
		addMember(team, team.getOwnerProfile());

		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO team_members (team_id, profile_id)
				VALUES (?, ?)
				""", team.getId(), team.getOwnerProfile().getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void pendingInvitationCannotBeDuplicatedEvenByAnotherInviter() {
		Team team = createTeam();
		Profile invitee = createProfile("Invitee");
		invite(team, invitee);
		Profile otherInviter = createProfile("Other inviter");
		TeamInvitation duplicate = invitation(team, invitee);
		duplicate.setInviterProfile(otherInviter);

		assertThatThrownBy(() -> invitationRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void selfInvitationIsRejected() {
		Team team = createTeam();

		assertThatThrownBy(() -> invite(team, team.getOwnerProfile()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@EnumSource(value = TeamInvitationStatus.class, names = {"DECLINED", "CANCELLED"})
	void resolvedInvitationIsRetainedAndDoesNotPreventNewInvitation(
			TeamInvitationStatus status
	) {
		Team team = createTeam();
		Profile invitee = createProfile("Invitee");
		TeamInvitation first = invite(team, invitee);
		first.setStatus(status);
		first.setResolvedAt(LocalDateTime.now());
		invitationRepository.saveAndFlush(first);

		TeamInvitation second = invite(team, invitee);

		TeamInvitation resolved = invitationRepository.findById(first.getId())
				.orElseThrow();
		assertThat(resolved.getStatus()).isEqualTo(status);
		assertThat(resolved.getResolvedAt()).isNotNull();
		assertThat(second.getId()).isNotEqualTo(first.getId());
		assertThat(second.getStatus()).isEqualTo(TeamInvitationStatus.PENDING);
	}

	@ParameterizedTest
	@EnumSource(value = TeamInvitationStatus.class, names = "PENDING", mode =
			EnumSource.Mode.EXCLUDE)
	void resolvedStatusRequiresResolutionTime(TeamInvitationStatus status) {
		TeamInvitation invitation = invite(createTeam(), createProfile("Invitee"));

		assertThatThrownBy(() -> jdbcTemplate.update("""
				UPDATE team_invitations SET status = ? WHERE id = ?
				""", status.name(), invitation.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void pendingInvitationCannotHaveResolutionTime() {
		TeamInvitation invitation = invite(createTeam(), createProfile("Invitee"));

		assertThatThrownBy(() -> jdbcTemplate.update("""
				UPDATE team_invitations SET resolved_at = NOW() WHERE id = ?
				""", invitation.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void resolutionCannotPrecedeInvitationCreation() {
		TeamInvitation invitation = invite(createTeam(), createProfile("Invitee"));

		assertThatThrownBy(() -> jdbcTemplate.update("""
				UPDATE team_invitations
				SET status = 'ACCEPTED', resolved_at = created_at - INTERVAL '1 second'
				WHERE id = ?
				""", invitation.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	void blankTeamNameIsRejectedByDatabase(String name) {
		Team team = createTeam();

		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE teams SET name = ? WHERE id = ?", name, team.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void unknownStatusesAreRejectedByDatabase() {
		Team team = createTeam();
		TeamInvitation invitation = invite(team, createProfile("Invitee"));

		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE teams SET status = 'UNKNOWN' WHERE id = ?", team.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
				UPDATE team_invitations SET status = 'UNKNOWN', resolved_at = NOW()
				WHERE id = ?
				""", invitation.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Team createTeam() {
		Team team = new Team();
		team.setOwnerProfile(createProfile("Owner"));
		team.setName("Hackathon team");
		team.setDescription("A service for students");
		return teamRepository.saveAndFlush(team);
	}

	private Profile createProfile(String displayName) {
		Profile profile = new Profile();
		profile.setDisplayName(displayName);
		return profileRepository.saveAndFlush(profile);
	}

	private TeamMember addMember(Team team, Profile profile) {
		TeamMember member = new TeamMember();
		member.setId(new TeamMemberId(team.getId(), profile.getId()));
		member.setTeam(team);
		member.setProfile(profile);
		return memberRepository.saveAndFlush(member);
	}

	private TeamInvitation invite(Team team, Profile invitee) {
		return invitationRepository.saveAndFlush(invitation(team, invitee));
	}

	private TeamInvitation invitation(Team team, Profile invitee) {
		TeamInvitation invitation = new TeamInvitation();
		invitation.setTeam(team);
		invitation.setInviterProfile(team.getOwnerProfile());
		invitation.setInviteeProfile(invitee);
		return invitation;
	}
}
