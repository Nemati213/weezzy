package ru.itmo.nemat.weezzy.team.invitation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.connection.ProfilePairLockService;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.connection.block.ProfileInteractionBlockedException;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanction;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionRepository;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;
import ru.itmo.nemat.weezzy.notification.NotificationRepository;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventRepository;
import ru.itmo.nemat.weezzy.outbox.OutboxEventService;
import ru.itmo.nemat.weezzy.outbox.OutboxEventStatus;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.handler.TeamInvitationResolvedEventHandler;
import ru.itmo.nemat.weezzy.outbox.handler.TeamInvitationSentEventHandler;
import ru.itmo.nemat.weezzy.outbox.payload.TeamInvitationResolvedPayload;
import ru.itmo.nemat.weezzy.outbox.payload.TeamInvitationSentPayload;
import ru.itmo.nemat.weezzy.outbox.worker.OutboxEventClaimService;
import ru.itmo.nemat.weezzy.outbox.worker.OutboxEventProcessor;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.team.TeamRepository;
import ru.itmo.nemat.weezzy.team.invitation.dto.CreateTeamInvitationRequest;
import ru.itmo.nemat.weezzy.team.member.TeamMember;
import ru.itmo.nemat.weezzy.team.member.TeamMemberId;
import ru.itmo.nemat.weezzy.team.member.TeamMemberRepository;
import ru.itmo.nemat.weezzy.user.AccountTemporarilySuspendedException;
import ru.itmo.nemat.weezzy.user.UserRepository;
import ru.itmo.nemat.weezzy.user.accountdeletion.AccountDeletionService;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false",
		"app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false"
})
class TeamInvitationControllerTests {
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
	private TeamInvitationRepository repository;

	@Autowired
	private TeamInvitationService service;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private TeamMemberRepository memberRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private AccountSanctionRepository sanctionRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountDeletionService deletionService;

	@Autowired
	private ProfilePairLockService pairLockService;

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

	@Autowired
	private TeamInvitationSentEventHandler sentHandler;

	@Autowired
	private TeamInvitationResolvedEventHandler resolvedHandler;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@MockitoSpyBean
	private OutboxEventService outboxEventService;

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
	void invitationIsPrivateAndAcceptanceAddsMemberExactlyOnce() throws Exception {
		Fixture fixture = fixture();
		mockMvc.perform(fixture.invitee().owner().authorize(get(invitationsMe())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(fixture.id().toString()))
				.andExpect(jsonPath("$.content[0].teamId").value(fixture.teamId().toString()))
				.andExpect(jsonPath("$.content[0].telegram").doesNotExist())
				.andExpect(jsonPath("$.content[0].email").doesNotExist())
				.andExpect(jsonPath("$.content[0].userId").doesNotExist());
		mockMvc.perform(fixture.owner().owner().authorize(get(invitationsMe())))
				.andExpect(jsonPath("$.totalElements").value(0));
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
		act(fixture.invitee(), fixture.id(), "accept")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.resolvedAt").isNotEmpty());
		LocalDateTime resolvedAt = invitation(fixture).getResolvedAt();
		act(fixture.invitee(), fixture.id(), "accept").andExpect(status().isOk());
		assertThat(invitation(fixture).getResolvedAt()).isEqualTo(resolvedAt);
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(2);
		assertThat(eventRepository.findAll()).hasSize(2);
		mockMvc.perform(fixture.invitee().owner().authorize(get(invitationsMe())))
				.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(fixture.invitee().owner().authorize(get(invitationsMe())
						.param("status", "ACCEPTED")))
				.andExpect(jsonPath("$.totalElements").value(1));
		mockMvc.perform(fixture.owner().owner().authorize(get("/api/teams/" + fixture.teamId())))
				.andExpect(jsonPath("$.members.length()").value(2));
	}

	@ParameterizedTest
	@ValueSource(strings = {"decline", "cancel"})
	void closingInvitationIsIdempotentAndAllowsReinviting(String action) throws Exception {
		Fixture fixture = fixture();
		TestProfile actor = action.equals("cancel") ? fixture.owner() : fixture.invitee();
		act(actor, fixture.id(), action).andExpect(status().isOk());
		LocalDateTime resolvedAt = invitation(fixture).getResolvedAt();
		act(actor, fixture.id(), action).andExpect(status().isOk());
		assertThat(invitation(fixture).getResolvedAt()).isEqualTo(resolvedAt);
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
		assertThat(eventRepository.findAll()).hasSize(2);
		act(fixture.invitee(), fixture.id(), "accept").andExpect(status().isConflict());
		send(fixture.owner(), fixture.teamId(), UUID.fromString(fixture.invitee().id()))
				.andExpect(status().isCreated());
	}

	@Test
	void allEndpointsRequireAuthentication() throws Exception {
		UUID id = UUID.randomUUID();
		mockMvc.perform(post("/api/teams/" + id + "/invitations")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"inviteeProfileId\":\"" + id + "\"}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get(invitationsMe())).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/team-invitations/" + id))
				.andExpect(status().isUnauthorized());
		for (String action : List.of("accept", "decline", "cancel")) {
			mockMvc.perform(post("/api/team-invitations/" + id + "/" + action))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	void onlyOwnerSendsAndOnlyIntendedActorCanResolveOrRead() throws Exception {
		Fixture fixture = fixture();
		TestProfile outsider = activeProfile("Outsider");
		send(outsider, fixture.teamId(), UUID.fromString(fixture.invitee().id()))
				.andExpect(status().isForbidden());
		for (String action : List.of("accept", "decline", "cancel")) {
			act(outsider, fixture.id(), action).andExpect(status().isNotFound());
		}
		act(fixture.owner(), fixture.id(), "accept").andExpect(status().isNotFound());
		act(fixture.owner(), fixture.id(), "decline").andExpect(status().isNotFound());
		act(fixture.invitee(), fixture.id(), "cancel").andExpect(status().isNotFound());
		mockMvc.perform(outsider.owner().authorize(get("/api/team-invitations/" + fixture.id())))
				.andExpect(status().isNotFound());
		for (TestProfile actor : List.of(fixture.owner(), fixture.invitee())) {
			mockMvc.perform(actor.owner().authorize(get("/api/team-invitations/" + fixture.id())))
					.andExpect(status().isOk());
		}
		mockMvc.perform(outsider.owner().authorize(get(invitationsMe())))
				.andExpect(jsonPath("$.totalElements").value(0));
		assertThat(invitation(fixture).getStatus()).isEqualTo(TeamInvitationStatus.PENDING);
	}

	@Test
	void duplicateSelfMemberMissingAndInvalidRequestsAreRejected() throws Exception {
		Fixture fixture = fixture();
		send(fixture.owner(), fixture.teamId(), UUID.fromString(fixture.invitee().id()))
				.andExpect(status().isConflict());
		send(fixture.owner(), fixture.teamId(), UUID.fromString(fixture.owner().id()))
				.andExpect(status().isBadRequest());
		send(fixture.owner(), fixture.teamId(), UUID.randomUUID()).andExpect(status().isNotFound());
		send(fixture.owner(), UUID.randomUUID(), UUID.fromString(fixture.invitee().id()))
				.andExpect(status().isNotFound());
		send(fixture.owner(), fixture.teamId(), null).andExpect(status().isBadRequest());
		act(fixture.invitee(), UUID.randomUUID(), "accept").andExpect(status().isNotFound());
		act(fixture.invitee(), fixture.id(), "accept").andExpect(status().isOk());
		send(fixture.owner(), fixture.teamId(), UUID.fromString(fixture.invitee().id()))
				.andExpect(status().isConflict());
		act(fixture.owner(), fixture.id(), "cancel").andExpect(status().isConflict());
		assertThat(eventRepository.findAll()).hasSize(2);
	}

	@ParameterizedTest
	@ValueSource(strings = {"page=-1", "size=0", "size=101", "status=INVALID"})
	void incomingPaginationValidatesParameters(String parameter) throws Exception {
		AuthenticatedTestUser user = AuthenticatedTestUser.register(mockMvc, objectMapper);
		mockMvc.perform(user.authorize(get(invitationsMe() + "?" + parameter)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void incomingPagesHaveStableOrderWithoutForeignInvitations() throws Exception {
		TestProfile owner = activeProfile("Owner");
		TestProfile invitee = activeProfile("Invitee");
		List<String> ids = new ArrayList<>();
		for (int index = 0; index < 3; index++) {
			ids.add(sendId(owner, createTeam(owner), invitee).toString());
		}
		jdbcTemplate.update("UPDATE team_invitations SET created_at = ? WHERE invitee_profile_id = ?",
				LocalDateTime.now().minusDays(1), UUID.fromString(invitee.id()));
		ids.sort(Comparator.reverseOrder());
		mockMvc.perform(invitee.owner().authorize(get(invitationsMe()).param("size", "2")))
				.andExpect(jsonPath("$.content[0].id").value(ids.get(0)))
				.andExpect(jsonPath("$.content[1].id").value(ids.get(1)))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.hasNext").value(true));
		mockMvc.perform(invitee.owner().authorize(get(invitationsMe())
						.param("size", "2").param("page", "1")))
				.andExpect(jsonPath("$.content[0].id").value(ids.get(2)))
				.andExpect(jsonPath("$.hasNext").value(false));
	}

	@ParameterizedTest
	@EnumSource(value = ProfileStatus.class, names = {"DRAFT", "HIDDEN", "DELETED"})
	void inactiveProfilesCannotBeInvitedOrJoin(ProfileStatus profileStatus) throws Exception {
		Fixture fixture = fixture();
		setStatus(fixture.invitee(), profileStatus);
		act(fixture.invitee(), fixture.id(), "accept").andExpect(status().isConflict());
		send(fixture.owner(), fixture.teamId(), UUID.fromString(fixture.invitee().id()))
				.andExpect(status().isConflict());
		mockMvc.perform(fixture.invitee().owner().authorize(get(invitationsMe())))
				.andExpect(jsonPath("$.totalElements").value(0));
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void ownerAndTeamEligibilityAreRecheckedOnAcceptance(boolean archived) throws Exception {
		Fixture fixture = fixture();
		if (archived) {
			jdbcTemplate.update("UPDATE teams SET status = 'ARCHIVED' WHERE id = ?", fixture.teamId());
		} else {
			setStatus(fixture.owner(), ProfileStatus.HIDDEN);
		}
		act(fixture.invitee(), fixture.id(), "accept").andExpect(status().isConflict());
		mockMvc.perform(fixture.invitee().owner().authorize(get(invitationsMe())))
				.andExpect(jsonPath("$.totalElements").value(0));
		act(fixture.invitee(), fixture.id(), "decline").andExpect(status().isOk());
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void blocksInEitherDirectionPreventInvitingAndJoining(boolean ownerBlocks) throws Exception {
		Fixture fixture = fixture();
		TestProfile blocker = ownerBlocks ? fixture.owner() : fixture.invitee();
		TestProfile blocked = ownerBlocks ? fixture.invitee() : fixture.owner();
		blockService.block(UUID.fromString(blocker.id()), UUID.fromString(blocked.id()));
		act(fixture.invitee(), fixture.id(), "accept").andExpect(status().isConflict());
		send(fixture.owner(), fixture.teamId(), UUID.fromString(fixture.invitee().id()))
				.andExpect(status().isConflict());
		mockMvc.perform(fixture.invitee().owner().authorize(get(invitationsMe())))
				.andExpect(jsonPath("$.totalElements").value(0));
		act(fixture.invitee(), fixture.id(), "decline").andExpect(status().isOk());
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
	}

	@Test
	void sanctionsAreRecheckedAndExpiredSanctionsDoNotPreventJoining() throws Exception {
		Fixture fixture = fixture();
		sanction(fixture.owner());
		act(fixture.invitee(), fixture.id(), "accept").andExpect(status().isNotFound());
		send(fixture.owner(), fixture.teamId(), UUID.fromString(fixture.invitee().id()))
				.andExpect(status().isForbidden());
		mockMvc.perform(fixture.invitee().owner().authorize(get(invitationsMe())))
				.andExpect(jsonPath("$.totalElements").value(0));
		jdbcTemplate.update("UPDATE account_sanctions SET expires_at = ? WHERE target_user_id = ?",
				LocalDateTime.now().minusMinutes(1), userId(fixture.owner()));
		act(fixture.invitee(), fixture.id(), "accept").andExpect(status().isOk());
	}

	@Test
	void bannedInviteeCannotBeInvitedOrAccept() throws Exception {
		Fixture fixture = fixture();
		sanction(fixture.invitee());
		act(fixture.invitee(), fixture.id(), "accept").andExpect(status().isForbidden());
		send(fixture.owner(), fixture.teamId(), UUID.fromString(fixture.invitee().id()))
				.andExpect(status().isNotFound());
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
	}

	@Test
	void membershipAddedThroughAnotherFlowPreventsAcceptance() throws Exception {
		Fixture fixture = fixture();
		TeamMember member = new TeamMember();
		member.setId(new TeamMemberId(fixture.teamId(), UUID.fromString(fixture.invitee().id())));
		member.setTeam(teamRepository.findById(fixture.teamId()).orElseThrow());
		member.setProfile(profileRepository.findById(UUID.fromString(fixture.invitee().id())).orElseThrow());
		memberRepository.saveAndFlush(member);
		act(fixture.invitee(), fixture.id(), "accept").andExpect(status().isConflict());
		assertThat(invitation(fixture).getStatus()).isEqualTo(TeamInvitationStatus.PENDING);
	}

	@Test
	void publishFailureRollsBackSending() throws Exception {
		TestProfile owner = activeProfile("Owner");
		UUID teamId = createTeam(owner);
		TestProfile invitee = activeProfile("Invitee");
		long count = repository.count();
		OutboxEventService publisher = AopTestUtils.getUltimateTargetObject(outboxEventService);
		doThrow(new IllegalStateException("Test publish failure"))
				.when(publisher).publish(any(TeamInvitationSentPayload.class));
		assertThatThrownBy(() -> send(owner, teamId, UUID.fromString(invitee.id())))
				.hasRootCauseInstanceOf(IllegalStateException.class);
		assertThat(repository.count()).isEqualTo(count);
		assertThat(eventRepository.findAll()).isEmpty();
	}

	@Test
	void publishFailureRollsBackDecisionAndMembership() throws Exception {
		Fixture fixture = fixture();
		OutboxEventService publisher = AopTestUtils.getUltimateTargetObject(outboxEventService);
		doThrow(new IllegalStateException("Test publish failure"))
				.when(publisher).publish(any(TeamInvitationResolvedPayload.class));
		assertThatThrownBy(() -> act(fixture.invitee(), fixture.id(), "accept"))
				.hasRootCauseInstanceOf(IllegalStateException.class);
		assertThat(invitation(fixture).getStatus()).isEqualTo(TeamInvitationStatus.PENDING);
		assertThat(invitation(fixture).getResolvedAt()).isNull();
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
		assertThat(eventRepository.findAll()).hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(strings = {"accept", "decline", "cancel"})
	void outboxDeliversNotificationsWithoutDuplicatesOrContacts(String action) throws Exception {
		Fixture fixture = fixture();
		OutboxEvent sent = eventRepository.findAll().getFirst();
		processEvents();
		sentHandler.handle(sent);
		assertThat(notificationRepository.findAll()).hasSize(1);
		assertThat(notificationRepository.findAll().getFirst().getType())
				.isEqualTo(NotificationType.TEAM_INVITATION_RECEIVED);
		TestProfile actor = action.equals("cancel") ? fixture.owner() : fixture.invitee();
		act(actor, fixture.id(), action).andExpect(status().isOk());
		processEvents();
		OutboxEvent resolved = eventRepository.findAll().stream()
				.filter(event -> event.getEventType() == OutboxEventType.TEAM_INVITATION_RESOLVED)
				.findFirst().orElseThrow();
		resolvedHandler.handle(resolved);
		assertThat(notificationRepository.findAll()).hasSize(2);
		NotificationType expectedType = switch (action) {
			case "accept" -> NotificationType.TEAM_INVITATION_ACCEPTED;
			case "decline" -> NotificationType.TEAM_INVITATION_DECLINED;
			default -> NotificationType.TEAM_INVITATION_CANCELLED;
		};
		UUID recipient = userId(action.equals("cancel") ? fixture.invitee() : fixture.owner());
		var notification = notificationRepository.findByRecipientUserIdAndSourceEventId(
				recipient, resolved.getId()
		).orElseThrow();
		assertThat(notification.getType()).isEqualTo(expectedType);
		assertThat(notification.getPayload()).containsOnlyKeys("teamId", "invitationId", "status");
		assertThat(eventRepository.findAll()).extracting(OutboxEvent::getStatus)
				.containsOnly(OutboxEventStatus.PROCESSED);
	}

	@Test
	void obsoleteSentEventDoesNotNotifyAfterAcceptance() throws Exception {
		Fixture fixture = fixture();
		act(fixture.invitee(), fixture.id(), "accept").andExpect(status().isOk());
		processEvents();
		assertThat(notificationRepository.findAll()).hasSize(1);
		assertThat(notificationRepository.findAll().getFirst().getType())
				.isEqualTo(NotificationType.TEAM_INVITATION_ACCEPTED);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void deletedParticipantsAreSkippedByOutbox(boolean deleteOwner) throws Exception {
		Fixture fixture = fixture();
		deletionService.deleteAccount(userId(deleteOwner ? fixture.owner() : fixture.invitee()),
				"password123");
		processEvents();
		assertThat(notificationRepository.findAll()).isEmpty();
		assertThat(eventRepository.findAll().getFirst().getStatus())
				.isEqualTo(OutboxEventStatus.PROCESSED);
		if (deleteOwner) {
			act(fixture.invitee(), fixture.id(), "decline").andExpect(status().isOk());
		}
	}

	@Test
	void blockedAndSanctionedParticipantsAreSkippedByOutbox() throws Exception {
		Fixture blocked = fixture();
		blockService.block(UUID.fromString(blocked.owner().id()), UUID.fromString(blocked.invitee().id()));
		Fixture banned = fixture();
		sanction(banned.owner());
		processEvents();
		assertThat(notificationRepository.findAll()).isEmpty();
		assertThat(eventRepository.findAll()).extracting(OutboxEvent::getStatus)
				.containsOnly(OutboxEventStatus.PROCESSED);
	}

	@Test
	void duplicateSendsAreSerialized() throws Exception {
		TestProfile owner = activeProfile("Owner");
		UUID teamId = createTeam(owner);
		TestProfile invitee = activeProfile("Invitee");
		Runnable send = () -> service.send(userId(owner), teamId, UUID.fromString(invitee.id()));
		List<RuntimeException> results = race(send, send);
		assertThat(results.stream().filter(result -> result == null).count()).isEqualTo(1);
		assertThat(results.stream().filter(result -> result != null).findFirst().orElseThrow())
				.isInstanceOf(DuplicatePendingTeamInvitationException.class);
		assertThat(eventRepository.findAll()).hasSize(1);
	}

	@Test
	void simultaneousAcceptsAddOneMemberAndOneDecisionEvent() throws Exception {
		Fixture fixture = fixture();
		Runnable accept = () -> service.accept(userId(fixture.invitee()), fixture.id());
		assertThat(race(accept, accept)).containsOnlyNulls();
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(2);
		assertThat(eventRepository.findAll()).hasSize(2);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void acceptanceRacingWithAnotherDecisionHasOneWinner(boolean cancel) throws Exception {
		Fixture fixture = fixture();
		List<RuntimeException> results = race(
				() -> service.accept(userId(fixture.invitee()), fixture.id()),
				() -> {
					if (cancel) {
						service.cancel(userId(fixture.owner()), fixture.id());
					} else {
						service.decline(userId(fixture.invitee()), fixture.id());
					}
				}
		);
		assertThat(results.stream().filter(result -> result == null).count()).isEqualTo(1);
		assertThat(results.stream().filter(result -> result != null).findFirst().orElseThrow())
				.isInstanceOf(TeamInvitationStatusConflictException.class);
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(
				invitation(fixture).getStatus() == TeamInvitationStatus.ACCEPTED ? 2 : 1
		);
		assertThat(eventRepository.findAll()).hasSize(2);
	}

	@Test
	void acceptanceWaitingForBlockSeesCommittedBlock() throws Exception {
		Fixture fixture = fixture();
		try (var executor = Executors.newSingleThreadExecutor()) {
			Future<RuntimeException> future = transactionTemplate.execute(transaction -> {
				pairLockService.lock(UUID.fromString(fixture.owner().id()),
						UUID.fromString(fixture.invitee().id()));
				Future<RuntimeException> pending = executor.submit(() -> capture(
						() -> service.accept(userId(fixture.invitee()), fixture.id())
				));
				awaitLock("from profiles");
				blockService.block(UUID.fromString(fixture.owner().id()),
						UUID.fromString(fixture.invitee().id()));
				return pending;
			});
			assertThat(future.get(20, TimeUnit.SECONDS))
					.isInstanceOf(ProfileInteractionBlockedException.class);
		}
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
	}

	@Test
	void acceptanceWaitingForSanctionSeesCommittedSanction() throws Exception {
		Fixture fixture = fixture();
		try (var executor = Executors.newSingleThreadExecutor()) {
			Future<RuntimeException> future = transactionTemplate.execute(transaction -> {
				userRepository.findByIdForUpdate(userId(fixture.invitee())).orElseThrow();
				Future<RuntimeException> pending = executor.submit(() -> capture(
						() -> service.accept(userId(fixture.invitee()), fixture.id())
				));
				awaitLock("from users");
				sanction(fixture.invitee());
				return pending;
			});
			assertThat(future.get(20, TimeUnit.SECONDS))
					.isInstanceOf(AccountTemporarilySuspendedException.class);
		}
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
	}

	private void awaitLock(String table) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			jdbcTemplate.execute("SELECT pg_stat_clear_snapshot()");
			Boolean waiting = jdbcTemplate.queryForObject("""
					SELECT EXISTS (
					    SELECT 1 FROM pg_stat_activity
					    WHERE wait_event_type = 'Lock' AND LOWER(query) LIKE ?
					)
					""", Boolean.class, "%" + table + "%");
			if (Boolean.TRUE.equals(waiting)) {
				return;
			}
			try {
				Thread.sleep(20);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
		}
		throw new AssertionError("Invitation operation did not wait for " + table + " lock");
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
					return capture(action);
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return Arrays.asList(futures.get(0).get(20, TimeUnit.SECONDS),
					futures.get(1).get(20, TimeUnit.SECONDS));
		}
	}

	private RuntimeException capture(Runnable action) {
		try {
			action.run();
			return null;
		} catch (RuntimeException exception) {
			return exception;
		}
	}

	private Fixture fixture() throws Exception {
		TestProfile owner = activeProfile("Owner");
		TestProfile invitee = activeProfile("Invitee");
		UUID teamId = createTeam(owner);
		return new Fixture(owner, invitee, teamId, sendId(owner, teamId, invitee));
	}

	private TestProfile activeProfile(String name) throws Exception {
		TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile(name);
		setStatus(profile, ProfileStatus.ACTIVE);
		return profile;
	}

	private void setStatus(TestProfile profile, ProfileStatus profileStatus) {
		jdbcTemplate.update("UPDATE profiles SET status = ? WHERE id = ?",
				profileStatus.name(), UUID.fromString(profile.id()));
	}

	private UUID createTeam(TestProfile owner) throws Exception {
		String response = mockMvc.perform(owner.owner().authorize(post("/api/teams"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Team\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(objectMapper.readTree(response).path("id").asText());
	}

	private ResultActions send(TestProfile owner, UUID teamId, UUID inviteeId) throws Exception {
		return mockMvc.perform(owner.owner().authorize(post("/api/teams/" + teamId + "/invitations"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new CreateTeamInvitationRequest(inviteeId))));
	}

	private UUID sendId(TestProfile owner, UUID teamId, TestProfile invitee) throws Exception {
		ResultActions result = send(owner, teamId, UUID.fromString(invitee.id()))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
		UUID id = UUID.fromString(objectMapper.readTree(result.andReturn().getResponse()
				.getContentAsString()).path("id").asText());
		result.andExpect(header().string("Location", "/api/team-invitations/" + id));
		return id;
	}

	private ResultActions act(TestProfile actor, UUID id, String action) throws Exception {
		return mockMvc.perform(actor.owner().authorize(post("/api/team-invitations/" + id + "/" + action)));
	}

	private TeamInvitation invitation(Fixture fixture) {
		return repository.findById(fixture.id()).orElseThrow();
	}

	private void processEvents() {
		LocalDateTime now = LocalDateTime.now().plusSeconds(1);
		claimService.claimBatch("team-invitation-test", 100, now)
				.forEach(id -> processor.process(id, "team-invitation-test", now));
	}

	private void sanction(TestProfile profile) {
		AccountSanction sanction = new AccountSanction();
		sanction.setTargetUserId(userId(profile));
		sanction.setTargetProfileId(UUID.fromString(profile.id()));
		sanction.setType(AccountSanctionType.TEMPORARY_SUSPENSION);
		sanction.setReason("Test sanction");
		sanction.setExpiresAt(LocalDateTime.now().plusHours(1));
		sanction.setCreatedByUserId(UUID.randomUUID());
		sanctionRepository.saveAndFlush(sanction);
	}

	private UUID userId(TestProfile profile) {
		return UUID.fromString(profile.owner().userId());
	}

	private String invitationsMe() {
		return "/api/team-invitations/me";
	}

	private record Fixture(TestProfile owner, TestProfile invitee, UUID teamId, UUID id) {
	}
}
