package ru.itmo.nemat.weezzy.team;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.common.exception.ConflictException;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.notification.NotificationRepository;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.outbox.OutboxEventRepository;
import ru.itmo.nemat.weezzy.outbox.OutboxEventService;
import ru.itmo.nemat.weezzy.outbox.OutboxEventStatus;
import ru.itmo.nemat.weezzy.outbox.payload.TeamInvitationResolvedPayload;
import ru.itmo.nemat.weezzy.outbox.worker.OutboxEventClaimService;
import ru.itmo.nemat.weezzy.outbox.worker.OutboxEventProcessor;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.team.dto.CreateTeamRequest;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationRepository;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationService;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationStatus;
import ru.itmo.nemat.weezzy.team.member.TeamMember;
import ru.itmo.nemat.weezzy.team.member.TeamMemberId;
import ru.itmo.nemat.weezzy.team.member.TeamMemberRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false",
		"app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false"
})
class TeamManagementTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy").withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private TeamService service;
	@Autowired
	private TeamRepository teamRepository;
	@Autowired
	private TeamMemberRepository memberRepository;
	@Autowired
	private ProfileRepository profileRepository;
	@Autowired
	private TeamInvitationRepository invitationRepository;
	@Autowired
	private TeamInvitationService invitationService;
	@Autowired
	private ProfileBlockService blockService;
	@Autowired
	private OutboxEventRepository eventRepository;
	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private OutboxEventClaimService claimService;
	@Autowired
	private OutboxEventProcessor processor;
	@MockitoSpyBean
	private OutboxEventService publisher;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void clearEvents() {
		notificationRepository.deleteAll();
		eventRepository.deleteAll();
	}

	@Test
	void memberLeavesAndCanRejoinOnlyWithNewInvitation() throws Exception {
		Fixture fixture = fixture(true);
		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(fixture.member().owner().authorize(
					delete(base(fixture) + "/members/me")))
					.andExpect(status().isNoContent());
		}
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
		invitationService.accept(userId(fixture.member()), fixture.invitationId());
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
		UUID fresh = invite(fixture, fixture.member());
		invitationService.accept(userId(fixture.member()), fresh);
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(2);
		assertThat(invitationStatus(fixture)).isEqualTo(TeamInvitationStatus.ACCEPTED);
	}

	@Test
	void ownerRemovesMemberAndRepeatingRemovalDoesNotChangeHistory() throws Exception {
		Fixture fixture = fixture(true);
		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(fixture.owner().owner().authorize(delete(
					base(fixture) + "/members/" + fixture.member().id()
			))).andExpect(status().isNoContent());
		}
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
		assertThat(eventRepository.count()).isEqualTo(2);
		invitationService.accept(userId(fixture.member()), fixture.invitationId());
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
		mockMvc.perform(fixture.owner().owner().authorize(get(base(fixture))))
				.andExpect(jsonPath("$.members.length()").value(1));
	}

	@Test
	void ownerCannotLeaveOrRemoveSelf() throws Exception {
		Fixture fixture = fixture(true);
		mockMvc.perform(fixture.owner().owner().authorize(
				delete(base(fixture) + "/members/me"))).andExpect(status().isConflict());
		mockMvc.perform(fixture.owner().owner().authorize(delete(
				base(fixture) + "/members/" + fixture.owner().id()
		))).andExpect(status().isConflict());
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(2);
	}

	@Test
	void memberAndOutsiderCannotManageAnotherMembershipOrArchive() throws Exception {
		Fixture fixture = fixture(true);
		TestProfile outsider = profile("Outsider");
		for (TestProfile actor : List.of(fixture.member(), outsider)) {
			mockMvc.perform(actor.owner().authorize(delete(
					base(fixture) + "/members/" + fixture.member().id()
			))).andExpect(status().isForbidden());
			mockMvc.perform(actor.owner().authorize(post(base(fixture) + "/archive")))
					.andExpect(status().isForbidden());
		}
		mockMvc.perform(outsider.owner().authorize(delete(base(fixture) + "/members/me")))
				.andExpect(status().isNoContent());
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(2);
	}

	@Test
	void allManagementEndpointsRequireAuthentication() throws Exception {
		String base = "/api/teams/" + UUID.randomUUID();
		mockMvc.perform(delete(base + "/members/me")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(base + "/members/" + UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post(base + "/archive")).andExpect(status().isUnauthorized());
	}

	@Test
	void missingTeamsProfilesAndInvalidIdsAreHandled() throws Exception {
		Fixture fixture = fixture(true);
		String missing = "/api/teams/" + UUID.randomUUID();
		mockMvc.perform(fixture.owner().owner().authorize(post(missing + "/archive")))
				.andExpect(status().isNotFound());
		mockMvc.perform(fixture.member().owner().authorize(delete(missing + "/members/me")))
				.andExpect(status().isNotFound());
		mockMvc.perform(fixture.owner().owner().authorize(delete(
				base(fixture) + "/members/" + UUID.randomUUID()
		))).andExpect(status().isNotFound());
		mockMvc.perform(fixture.owner().owner().authorize(
				delete(base(fixture) + "/members/invalid"))).andExpect(status().isBadRequest());
		AuthenticatedTestUser noProfile = AuthenticatedTestUser.register(mockMvc, objectMapper);
		mockMvc.perform(noProfile.authorize(delete(base(fixture) + "/members/me")))
				.andExpect(status().isNotFound());
	}

	@ParameterizedTest
	@EnumSource(value = ProfileStatus.class, names = {"DRAFT", "HIDDEN", "DELETED"})
	void inactiveMemberCanBeRemoved(ProfileStatus profileStatus) throws Exception {
		Fixture fixture = fixture(true);
		setStatus(fixture.member(), profileStatus);
		mockMvc.perform(fixture.owner().owner().authorize(delete(
				base(fixture) + "/members/" + fixture.member().id()
		))).andExpect(status().isNoContent());
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
	}

	@Test
	void hiddenMemberCanLeaveDespiteOwnerBlock() throws Exception {
		Fixture fixture = fixture(true);
		blockService.block(UUID.fromString(fixture.owner().id()),
				UUID.fromString(fixture.member().id()));
		setStatus(fixture.member(), ProfileStatus.HIDDEN);
		mockMvc.perform(fixture.member().owner().authorize(
				delete(base(fixture) + "/members/me"))).andExpect(status().isNoContent());
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
	}

	@Test
	void archiveKeepsMembersCancelsOnlyPendingAndDeliversCancellationsOnce() throws Exception {
		Fixture fixture = fixture(true);
		TestProfile first = profile("First invitee");
		TestProfile second = profile("Second invitee");
		UUID firstInvitation = invite(fixture, first);
		UUID secondInvitation = invite(fixture, second);
		mockMvc.perform(fixture.owner().owner().authorize(post(base(fixture) + "/archive")))
				.andExpect(status().isNoContent());
		LocalDateTime updatedAt = teamRepository.findById(fixture.teamId())
				.orElseThrow().getUpdatedAt();
		mockMvc.perform(fixture.owner().owner().authorize(post(base(fixture) + "/archive")))
				.andExpect(status().isNoContent());
		assertThat(teamRepository.findById(fixture.teamId()).orElseThrow().getStatus())
				.isEqualTo(TeamStatus.ARCHIVED);
		assertThat(teamRepository.findById(fixture.teamId()).orElseThrow().getUpdatedAt())
				.isEqualTo(updatedAt);
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(2);
		for (UUID id : List.of(firstInvitation, secondInvitation)) {
			var invitation = invitationRepository.findById(id).orElseThrow();
			assertThat(invitation.getStatus()).isEqualTo(TeamInvitationStatus.CANCELLED);
			assertThat(invitation.getResolvedAt()).isNotNull();
		}
		assertThat(invitationStatus(fixture)).isEqualTo(TeamInvitationStatus.ACCEPTED);
		assertThat(eventRepository.count()).isEqualTo(6);
		mockMvc.perform(first.owner().authorize(get("/api/team-invitations/me")))
				.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(fixture.owner().owner().authorize(get(base(fixture))))
				.andExpect(status().isNotFound());
		processEvents();
		assertThat(notificationRepository.findAll()).hasSize(2)
				.extracting(notification -> notification.getType())
				.containsOnly(NotificationType.TEAM_INVITATION_CANCELLED);
		assertThat(eventRepository.findAll()).allSatisfy(event ->
				assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED));
	}

	@Test
	void archivedTeamRejectsNewInvitationsJoiningLeavingAndRemoving() throws Exception {
		Fixture fixture = fixture(false);
		service.archiveForUser(userId(fixture.owner()), fixture.teamId());
		assertThatThrownBy(() -> invitationService.send(userId(fixture.owner()),
				fixture.teamId(), UUID.fromString(fixture.member().id())))
				.isInstanceOf(TeamNotActiveException.class);
		assertThatThrownBy(() -> invitationService.accept(userId(fixture.member()),
				fixture.invitationId())).isInstanceOf(ConflictException.class);
		mockMvc.perform(fixture.member().owner().authorize(
				delete(base(fixture) + "/members/me"))).andExpect(status().isConflict());
		mockMvc.perform(fixture.owner().owner().authorize(delete(
				base(fixture) + "/members/" + fixture.member().id()
		))).andExpect(status().isConflict());
	}

	@Test
	void hiddenOwnerCanArchiveAndExcludingPendingInviteeRevokesInvitation() throws Exception {
		Fixture fixture = fixture(false);
		mockMvc.perform(fixture.owner().owner().authorize(delete(
				base(fixture) + "/members/" + fixture.member().id()
		))).andExpect(status().isNoContent());
		assertThat(invitationStatus(fixture)).isEqualTo(TeamInvitationStatus.CANCELLED);
		setStatus(fixture.owner(), ProfileStatus.HIDDEN);
		mockMvc.perform(fixture.owner().owner().authorize(post(base(fixture) + "/archive")))
				.andExpect(status().isNoContent());
	}

	@Test
	void failedCancellationPublicationRollsBackArchiveAndInvitationChanges() throws Exception {
		Fixture fixture = fixture(false);
		OutboxEventService target = AopTestUtils.getUltimateTargetObject(publisher);
		doThrow(new IllegalStateException("Test publish failure"))
				.when(target).publish(any(TeamInvitationResolvedPayload.class));
		assertThatThrownBy(() -> service.archiveForUser(userId(fixture.owner()), fixture.teamId()))
				.isInstanceOf(IllegalStateException.class);
		assertThat(teamRepository.findById(fixture.teamId()).orElseThrow().getStatus())
				.isEqualTo(TeamStatus.ACTIVE);
		assertThat(invitationStatus(fixture)).isEqualTo(TeamInvitationStatus.PENDING);
		assertThat(eventRepository.count()).isEqualTo(1);
	}

	@Test
	void failedPendingCancellationRollsBackMembershipRemoval() throws Exception {
		Fixture fixture = fixture(false);
		TeamMember member = new TeamMember();
		member.setId(new TeamMemberId(fixture.teamId(), UUID.fromString(fixture.member().id())));
		member.setTeam(teamRepository.findById(fixture.teamId()).orElseThrow());
		member.setProfile(profileRepository.findById(
				UUID.fromString(fixture.member().id())).orElseThrow());
		memberRepository.saveAndFlush(member);
		OutboxEventService target = AopTestUtils.getUltimateTargetObject(publisher);
		doThrow(new IllegalStateException("Test publish failure"))
				.when(target).publish(any(TeamInvitationResolvedPayload.class));
		assertThatThrownBy(() -> service.removeMemberForUser(userId(fixture.owner()),
				fixture.teamId(), UUID.fromString(fixture.member().id())))
				.isInstanceOf(IllegalStateException.class);
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(2);
		assertThat(invitationStatus(fixture)).isEqualTo(TeamInvitationStatus.PENDING);
	}

	@Test
	void concurrentArchiveAndAcceptanceAreSerialized() throws Exception {
		Fixture fixture = fixture(false);
		List<RuntimeException> outcomes = race(
				() -> service.archiveForUser(userId(fixture.owner()), fixture.teamId()),
				() -> invitationService.accept(userId(fixture.member()), fixture.invitationId()));
		assertThat(outcomes.get(0)).isNull();
		if (outcomes.get(1) != null) {
			assertThat(outcomes.get(1)).isInstanceOf(ConflictException.class);
		}
		assertThat(teamRepository.findById(fixture.teamId()).orElseThrow().getStatus())
				.isEqualTo(TeamStatus.ARCHIVED);
		assertThat(invitationStatus(fixture)).isIn(
				TeamInvitationStatus.ACCEPTED, TeamInvitationStatus.CANCELLED);
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(
				invitationStatus(fixture) == TeamInvitationStatus.ACCEPTED ? 2 : 1);
		assertThat(eventRepository.count()).isEqualTo(2);
	}

	@Test
	void concurrentArchiveAndSendLeaveNoPendingInvitation() throws Exception {
		Fixture fixture = fixture(true);
		TestProfile invitee = profile("New invitee");
		List<RuntimeException> outcomes = race(
				() -> service.archiveForUser(userId(fixture.owner()), fixture.teamId()),
				() -> invitationService.send(userId(fixture.owner()), fixture.teamId(),
						UUID.fromString(invitee.id())));
		assertThat(outcomes.get(0)).isNull();
		if (outcomes.get(1) != null) {
			assertThat(outcomes.get(1)).isInstanceOf(TeamNotActiveException.class);
		}
		assertThat(invitationRepository.existsByTeamIdAndInviteeProfileIdAndStatus(
				fixture.teamId(), UUID.fromString(invitee.id()), TeamInvitationStatus.PENDING
		)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void concurrentLeaveAndRemovalOrAcceptedRetryDoNotRejoin(boolean remove) throws Exception {
		Fixture fixture = fixture(true);
		assertThat(race(
				() -> service.leaveForUser(userId(fixture.member()), fixture.teamId()),
				() -> {
					if (remove) {
						service.removeMemberForUser(userId(fixture.owner()), fixture.teamId(),
								UUID.fromString(fixture.member().id()));
					} else {
						invitationService.accept(userId(fixture.member()), fixture.invitationId());
					}
				}
		)).containsOnlyNulls();
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
	}

	@Test
	void concurrentExclusionAndAcceptanceCannotLeaveMemberInTeam() throws Exception {
		Fixture fixture = fixture(false);
		List<RuntimeException> outcomes = race(
				() -> service.removeMemberForUser(userId(fixture.owner()), fixture.teamId(),
						UUID.fromString(fixture.member().id())),
				() -> invitationService.accept(userId(fixture.member()), fixture.invitationId()));
		assertThat(outcomes.get(0)).isNull();
		if (outcomes.get(1) != null) {
			assertThat(outcomes.get(1)).isInstanceOf(ConflictException.class);
		}
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
	}

	private List<RuntimeException> race(Runnable first, Runnable second) throws Exception {
		try (var executor = Executors.newFixedThreadPool(2)) {
			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch start = new CountDownLatch(1);
			List<Future<RuntimeException>> futures = new ArrayList<>();
			for (Runnable action : List.of(first, second)) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					if (!start.await(10, TimeUnit.SECONDS)) {
						throw new AssertionError("Race did not start");
					}
					try {
						action.run();
						return null;
					} catch (RuntimeException exception) {
						return exception;
					}
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return Arrays.asList(futures.get(0).get(20, TimeUnit.SECONDS),
					futures.get(1).get(20, TimeUnit.SECONDS));
		}
	}

	private Fixture fixture(boolean join) throws Exception {
		TestProfile owner = profile("Owner");
		TestProfile member = profile("Member");
		UUID teamId = service.createForUser(userId(owner),
				new CreateTeamRequest("Team", null)).id();
		UUID id = invitationService.send(userId(owner), teamId, UUID.fromString(member.id())).id();
		if (join) {
			invitationService.accept(userId(member), id);
		}
		return new Fixture(owner, member, teamId, id);
	}

	private TestProfile profile(String name) throws Exception {
		TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile(name);
		setStatus(profile, ProfileStatus.ACTIVE);
		return profile;
	}

	private void setStatus(TestProfile profile, ProfileStatus profileStatus) {
		jdbcTemplate.update("UPDATE profiles SET status = ? WHERE id = ?",
				profileStatus.name(), UUID.fromString(profile.id()));
	}

	private UUID invite(Fixture fixture, TestProfile invitee) {
		return invitationService.send(userId(fixture.owner()), fixture.teamId(),
				UUID.fromString(invitee.id())).id();
	}

	private TeamInvitationStatus invitationStatus(Fixture fixture) {
		return invitationRepository.findById(fixture.invitationId()).orElseThrow().getStatus();
	}

	private void processEvents() {
		LocalDateTime now = LocalDateTime.now().plusSeconds(1);
		claimService.claimBatch("team-management-test", 100, now).forEach(id ->
				processor.process(id, "team-management-test", now));
	}

	private UUID userId(TestProfile profile) {
		return UUID.fromString(profile.owner().userId());
	}

	private String base(Fixture fixture) {
		return "/api/teams/" + fixture.teamId();
	}

	private record Fixture(TestProfile owner, TestProfile member, UUID teamId, UUID invitationId) {
	}
}
