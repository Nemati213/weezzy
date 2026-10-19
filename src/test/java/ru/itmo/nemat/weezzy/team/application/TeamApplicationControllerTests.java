package ru.itmo.nemat.weezzy.team.application;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
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
import ru.itmo.nemat.weezzy.outbox.handler.TeamApplicationChangedEventHandler;
import ru.itmo.nemat.weezzy.outbox.payload.TeamApplicationChangedPayload;
import ru.itmo.nemat.weezzy.outbox.worker.OutboxEventClaimService;
import ru.itmo.nemat.weezzy.outbox.worker.OutboxEventProcessor;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.team.TeamAccessService;
import ru.itmo.nemat.weezzy.team.TeamService;
import ru.itmo.nemat.weezzy.team.dto.CreateTeamRequest;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationService;
import ru.itmo.nemat.weezzy.team.member.TeamMemberService;
import ru.itmo.nemat.weezzy.team.position.TeamPositionCapacityException;
import ru.itmo.nemat.weezzy.team.position.TeamPositionService;
import ru.itmo.nemat.weezzy.team.position.dto.CreateTeamPositionRequest;
import ru.itmo.nemat.weezzy.team.position.dto.UpdateTeamPositionRequest;
import ru.itmo.nemat.weezzy.team.request.TeamRequestService;
import ru.itmo.nemat.weezzy.team.request.dto.CreateRecruitmentRequest;
import ru.itmo.nemat.weezzy.team.request.dto.TeamRequestResponse;
import ru.itmo.nemat.weezzy.team.request.dto.UpdateRecruitmentRequest;
import ru.itmo.nemat.weezzy.team.application.dto.CreateTeamApplicationRequest;
import ru.itmo.nemat.weezzy.team.application.dto.TeamApplicationResponse;
import ru.itmo.nemat.weezzy.user.accountdeletion.AccountDeletionService;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
class TeamApplicationControllerTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"))
			.withDatabaseName("weezzy").withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private TeamApplicationService service;
	@Autowired
	private TeamApplicationRepository repository;
	@Autowired
	private TeamRequestService requestService;
	@Autowired
	private TeamPositionService positionService;
	@Autowired
	private TeamService teamService;
	@Autowired
	private TeamAccessService teamAccessService;
	@Autowired
	private TeamInvitationService invitationService;
	@Autowired
	private TeamMemberService memberService;
	@Autowired
	private ProfileBlockService blockService;
	@Autowired
	private AccountSanctionRepository sanctionRepository;
	@Autowired
	private AccountDeletionService deletionService;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private OutboxEventRepository eventRepository;
	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private OutboxEventClaimService claimService;
	@Autowired
	private OutboxEventProcessor processor;
	@Autowired
	private TeamApplicationChangedEventHandler handler;
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
	void apiCreatesPrivateApplicationAndAcceptsExactlyOnce() throws Exception {
		Fixture fixture = fixture(1);
		String body = send(fixture.applicant(), fixture.positionId(), "  I can help  ")
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.message").value("I can help"))
				.andExpect(jsonPath("$.applicantProfileId").value(fixture.applicant().id()))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.email").doesNotExist())
				.andExpect(jsonPath("$.telegram").doesNotExist())
				.andExpect(jsonPath("$.userId").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		UUID id = UUID.fromString(objectMapper.readTree(body).path("id").asText());
		for (TestProfile participant : List.of(fixture.owner(), fixture.applicant())) {
			read(participant, id).andExpect(status().isOk());
		}
		act(fixture.owner(), id, "accept").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.resolvedAt").isNotEmpty());
		act(fixture.owner(), id, "accept").andExpect(status().isOk());
		assertThat(memberService.countByTeam(fixture.teamId())).isEqualTo(2);
		assertThat(memberService.countByPosition(teamPositionId(fixture))).isEqualTo(1);
		assertThat(events(id)).hasSize(2);
		var request = requestService.findByIdForUser(userId(fixture.owner()), fixture.requestId());
		assertThat(request.availableTeamSlotsCount()).isZero();
		assertThat(request.positions().getFirst().filledSlotsCount()).isEqualTo(1);
		assertThat(request.positions().getFirst().remainingSlotsCount()).isZero();
		requestService.updateForUser(userId(fixture.owner()), fixture.requestId(),
				new UpdateRecruitmentRequest("Updated", null,
						LocalDateTime.now().plusDays(2)));
	}

	@ParameterizedTest
	@ValueSource(strings = {"accept", "decline", "withdraw"})
	void decisionsAreIdempotentAndOtherDecisionsConflict(String action) throws Exception {
		Fixture fixture = fixture(1);
		UUID id = apply(fixture).id();
		TestProfile actor = action.equals("withdraw") ? fixture.applicant() : fixture.owner();
		act(actor, id, action).andExpect(status().isOk());
		act(actor, id, action).andExpect(status().isOk());
		if (!action.equals("accept")) {
			act(fixture.owner(), id, "accept").andExpect(status().isConflict());
			assertThat(memberService.countByPosition(teamPositionId(fixture))).isZero();
			send(fixture.applicant(), fixture.positionId(), "")
					.andExpect(status().isCreated()).andExpect(jsonPath("$.message").isEmpty());
		} else {
			act(fixture.applicant(), id, "withdraw").andExpect(status().isConflict());
		}
		assertThat(events(id)).hasSize(2);
	}

	@Test
	void rejectsDuplicateMemberAndOwnerApplications() throws Exception {
		Fixture fixture = fixture(2);
		apply(fixture);
		send(fixture.applicant(), fixture.positionId(), "").andExpect(status().isConflict());
		send(fixture.owner(), fixture.positionId(), "").andExpect(status().isConflict());
		invite(fixture, fixture.applicant());
		send(fixture.applicant(), fixture.positionId(), "").andExpect(status().isConflict());
		assertThat(memberService.countByPosition(teamPositionId(fixture))).isEqualTo(1);
	}

	@Test
	void authorizationAndValidationCoverEveryEndpoint() throws Exception {
		Fixture fixture = fixture(1);
		UUID id = apply(fixture).id();
		TestProfile outsider = profile("Outsider");
		read(outsider, id).andExpect(status().isNotFound());
		for (String action : List.of("accept", "decline", "withdraw")) {
			act(outsider, id, action).andExpect(status().isNotFound());
		}
		act(fixture.applicant(), id, "accept").andExpect(status().isNotFound());
		act(fixture.applicant(), id, "decline").andExpect(status().isNotFound());
		act(fixture.owner(), id, "withdraw").andExpect(status().isNotFound());
		mockMvc.perform(outsider.owner().authorize(get(ownerList(fixture))))
				.andExpect(status().isForbidden());
		send(outsider, fixture.positionId(), "x".repeat(2001))
				.andExpect(status().isBadRequest());
		send(outsider, UUID.randomUUID(), "").andExpect(status().isNotFound());
		read(fixture.owner(), UUID.randomUUID()).andExpect(status().isNotFound());
		for (String query : List.of("page=-1", "size=0", "size=101", "status=UNKNOWN")) {
			mockMvc.perform(fixture.owner().owner().authorize(
					get(ownerList(fixture) + "?" + query))).andExpect(status().isBadRequest());
			mockMvc.perform(fixture.applicant().owner().authorize(
					get("/api/team-applications/me?" + query)))
					.andExpect(status().isBadRequest());
		}
		List<MockHttpServletRequestBuilder> requests = List.of(
				post(sendUrl(fixture.positionId())).contentType(MediaType.APPLICATION_JSON)
						.content("{}"),
				get(base(id)), get("/api/team-applications/me"), get(ownerList(fixture)),
				post(base(id) + "/accept"), post(base(id) + "/decline"),
				post(base(id) + "/withdraw"));
		for (var request : requests) {
			mockMvc.perform(request).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void listsArePaginatedFilteredAndPrivate() throws Exception {
		Fixture fixture = fixture(2);
		UUID first = apply(fixture).id();
		service.withdraw(userId(fixture.applicant()), first);
		UUID second = apply(fixture).id();
		for (String url : List.of(ownerList(fixture), "/api/team-applications/me")) {
			TestProfile actor = url.equals(ownerList(fixture))
					? fixture.owner() : fixture.applicant();
			mockMvc.perform(actor.owner().authorize(get(url).param("size", "1")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElements").value(2))
					.andExpect(jsonPath("$.hasNext").value(true))
					.andExpect(jsonPath("$.content[0].id").value(second.toString()));
			mockMvc.perform(actor.owner().authorize(get(url).param("status", "WITHDRAWN")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElements").value(1))
					.andExpect(jsonPath("$.content[0].id").value(first.toString()));
		}
		mockMvc.perform(fixture.owner().owner().authorize(get(ownerList(fixture))
				.param("positionId", fixture.positionId().toString())))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
		Fixture other = fixture(1);
		mockMvc.perform(fixture.owner().owner().authorize(get(ownerList(fixture))
				.param("positionId", other.positionId().toString())))
				.andExpect(status().isNotFound());
		mockMvc.perform(other.applicant().owner().authorize(get("/api/team-applications/me")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
	}

	@ParameterizedTest
	@ValueSource(strings = {"closed", "expired", "archived", "blocked", "owner-inactive",
			"applicant-inactive", "owner-sanction", "applicant-sanction", "deleted-owner",
			"deleted-applicant"})
	void restrictionsPreventAcceptanceAndNewApplications(String restriction) throws Exception {
		Fixture fixture = fixture(1);
		UUID id = apply(fixture).id();
		restrict(fixture, restriction);
		assertThat(capture(() -> service.accept(userId(fixture.owner()), id))).isNotNull();
		assertThat(capture(() -> service.send(userId(fixture.applicant()),
				fixture.positionId(), new CreateTeamApplicationRequest(null)))).isNotNull();
		assertThat(repository.findById(id).orElseThrow().getStatus())
				.isEqualTo(TeamApplicationStatus.PENDING);
		assertThat(memberService.countByPosition(teamPositionId(fixture))).isZero();
		assertThat(events(id)).hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(strings = {"closed", "expired", "archived", "blocked",
			"owner-inactive", "applicant-inactive"})
	void restrictionsDoNotPreventWithdrawalOrDecline(String restriction) throws Exception {
		Fixture fixture = fixture(2);
		UUID id = apply(fixture).id();
		TestProfile another = profile("Another");
		UUID otherId = service.send(userId(another), fixture.positionId(),
				new CreateTeamApplicationRequest(null)).id();
		restrict(fixture, restriction);
		act(fixture.applicant(), id, "withdraw").andExpect(status().isOk());
		act(fixture.owner(), otherId, "decline").andExpect(status().isOk());
		read(fixture.owner(), id).andExpect(status().isOk());
		read(fixture.applicant(), id).andExpect(status().isOk());
	}

	@Test
	void invitationsAndApplicationsConsumeTheSamePositionCapacity() throws Exception {
		Fixture fixture = fixture(2);
		UUID id = apply(fixture).id();
		invite(fixture, profile("Invited"));
		service.accept(userId(fixture.owner()), id);
		assertThat(memberService.countByPosition(teamPositionId(fixture))).isEqualTo(2);
		var request = requestService.findByIdForUser(userId(fixture.owner()), fixture.requestId());
		assertThat(request.positions().getFirst().remainingSlotsCount()).isZero();
		assertThat(request.availableTeamSlotsCount()).isZero();
		TestProfile another = profile("Another");
		send(another, fixture.positionId(), "").andExpect(status().isConflict());
		positionService.updateForUser(userId(fixture.owner()), fixture.teamId(),
				teamPositionId(fixture), new UpdateTeamPositionRequest("Backend", null, 3, Set.of()));
		send(another, fixture.positionId(), "").andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void leavingOrRemovalReleasesPositionWithoutReplayingHistory(boolean remove)
			throws Exception {
		Fixture fixture = fixture(1);
		UUID id = apply(fixture).id();
		service.accept(userId(fixture.owner()), id);
		if (remove) {
			teamService.removeMemberForUser(userId(fixture.owner()), fixture.teamId(),
					profileId(fixture.applicant()));
		} else {
			teamService.leaveForUser(userId(fixture.applicant()), fixture.teamId());
		}
		service.accept(userId(fixture.owner()), id);
		assertThat(memberService.countByTeam(fixture.teamId())).isEqualTo(1);
		assertThat(memberService.countByPosition(teamPositionId(fixture))).isZero();
		invite(fixture, fixture.applicant());
		assertThat(memberService.countByPosition(teamPositionId(fixture))).isEqualTo(1);
		teamService.leaveForUser(userId(fixture.applicant()), fixture.teamId());
		UUID next = apply(fixture).id();
		service.accept(userId(fixture.owner()), next);
		assertThat(memberService.countByPosition(teamPositionId(fixture))).isEqualTo(1);
		assertThat(repository.findById(id).orElseThrow().getStatus())
				.isEqualTo(TeamApplicationStatus.ACCEPTED);
	}

	@Test
	void concurrentApplicationsAndLastPlaceAreSerialized() throws Exception {
		Fixture fixture = fixture(1);
		var duplicateResults = race(() -> apply(fixture), () -> apply(fixture));
		assertOneSuccess(duplicateResults);
		assertThat(duplicateResults.stream().filter(result -> result != null).findFirst().orElseThrow())
				.isInstanceOf(DuplicatePendingTeamApplicationException.class);
		UUID first = repository.findOwn(profileId(fixture.applicant()), null,
				org.springframework.data.domain.Pageable.unpaged()).getContent().getFirst().getId();
		TestProfile another = profile("Another");
		UUID second = service.send(userId(another), fixture.positionId(),
				new CreateTeamApplicationRequest(null)).id();
		assertOneSuccess(race(() -> service.accept(userId(fixture.owner()), first),
				() -> service.accept(userId(fixture.owner()), second)));
		assertThat(memberService.countByTeam(fixture.teamId())).isEqualTo(2);
		assertThat(memberService.countByPosition(teamPositionId(fixture))).isEqualTo(1);
	}

	@Test
	void simultaneousApplicationsAcrossRequestsSharePositionCapacity() throws Exception {
		Fixture fixture = fixture(1);
		TeamRequestResponse other = requestService.createForUser(userId(fixture.owner()),
				fixture.teamId(), new CreateRecruitmentRequest("Another advertisement", null,
				LocalDateTime.now().plusDays(2), Set.of(teamPositionId(fixture))));
		UUID first = apply(fixture).id();
		TestProfile another = profile("Another");
		UUID second = service.send(userId(another), other.positions().getFirst().id(),
				new CreateTeamApplicationRequest(null)).id();
		var results = race(() -> service.accept(userId(fixture.owner()), first),
				() -> service.accept(userId(fixture.owner()), second));
		assertOneSuccess(results);
		assertThat(results.stream().filter(result -> result != null).findFirst().orElseThrow())
				.isInstanceOf(TeamPositionCapacityException.class);
		assertThat(memberService.countByTeam(fixture.teamId())).isEqualTo(2);
	}

	@Test
	void invitationAndApplicationForDifferentCandidatesShareLastPlace() throws Exception {
		Fixture fixture = fixture(1);
		UUID application = apply(fixture).id();
		TestProfile invited = profile("Invited");
		UUID invitation = invitationService.send(userId(fixture.owner()), fixture.teamId(),
				profileId(invited), teamPositionId(fixture)).id();
		var results = race(() -> service.accept(userId(fixture.owner()), application),
				() -> invitationService.accept(userId(invited), invitation));
		assertOneSuccess(results);
		assertThat(results.stream().filter(result -> result != null).findFirst().orElseThrow())
				.isInstanceOf(TeamPositionCapacityException.class);
		assertThat(memberService.countByPosition(teamPositionId(fixture))).isEqualTo(1);
		assertThat(memberService.countByTeam(fixture.teamId())).isEqualTo(2);
	}

	@Test
	void positionsHaveIndependentCapacityAndSameApplicantCanApplyToBoth() throws Exception {
		Fixture fixture = fixture(1);
		var request = requestService.createForUser(userId(fixture.owner()), fixture.teamId(),
				new CreateRecruitmentRequest("Two roles", null,
						LocalDateTime.now().plusDays(2), Set.of(
						createPosition(fixture.owner(), fixture.teamId(), "Backend", 1),
						createPosition(fixture.owner(), fixture.teamId(), "Design", 1))));
		UUID backend = request.positions().getFirst().id();
		UUID design = request.positions().getLast().id();
		UUID first = service.send(userId(fixture.applicant()), backend,
				new CreateTeamApplicationRequest(null)).id();
		UUID otherRole = service.send(userId(fixture.applicant()), design,
				new CreateTeamApplicationRequest(null)).id();
		service.accept(userId(fixture.owner()), first);
		act(fixture.owner(), otherRole, "accept").andExpect(status().isConflict());
		TestProfile designer = profile("Designer");
		send(designer, backend, "").andExpect(status().isConflict());
		UUID second = service.send(userId(designer), design,
				new CreateTeamApplicationRequest(null)).id();
		service.accept(userId(fixture.owner()), second);
		var full = requestService.findByIdForUser(userId(fixture.owner()), request.id());
		assertThat(full.positions()).allSatisfy(position -> {
			assertThat(position.filledSlotsCount()).isEqualTo(1);
			assertThat(position.remainingSlotsCount()).isZero();
		});
	}

	@Test
	void lateInvitationLeavesPendingApplicationUnaccepted() throws Exception {
		Fixture fixture = fixture(1);
		UUID id = apply(fixture).id();
		invite(fixture, fixture.applicant());
		act(fixture.owner(), id, "accept").andExpect(status().isConflict());
		assertThat(memberService.countByPosition(teamPositionId(fixture))).isEqualTo(1);
		act(fixture.applicant(), id, "withdraw").andExpect(status().isOk());
	}

	@Test
	void concurrentNotificationDeliveryIsIdempotent() throws Exception {
		Fixture fixture = fixture(1);
		UUID id = apply(fixture).id();
		OutboxEvent event = events(id).getFirst();
		assertThat(race(() -> handler.handle(event), () -> handler.handle(event)))
				.containsOnlyNulls();
		assertThat(notificationRepository.findAll()).hasSize(1);
	}

	@Test
	void invitationAndApplicationCannotAddSamePersonTwice() throws Exception {
		Fixture fixture = fixture(1);
		UUID id = apply(fixture).id();
		UUID invitationId = invitationService.send(userId(fixture.owner()), fixture.teamId(),
				profileId(fixture.applicant()), teamPositionId(fixture)).id();
		assertOneSuccess(race(() -> service.accept(userId(fixture.owner()), id),
				() -> invitationService.accept(userId(fixture.applicant()), invitationId)));
		assertThat(memberService.countByTeam(fixture.teamId())).isEqualTo(2);
		assertThat(memberService.countByPosition(teamPositionId(fixture))).isEqualTo(1);
	}

	@Test
	void competingDecisionsAndRepeatedAcceptanceAreSafe() throws Exception {
		Fixture fixture = fixture(1);
		UUID id = apply(fixture).id();
		assertOneSuccess(race(() -> service.accept(userId(fixture.owner()), id),
				() -> service.withdraw(userId(fixture.applicant()), id)));
		assertThat(events(id)).hasSize(2);
		Fixture second = fixture(1);
		UUID secondId = apply(second).id();
		assertThat(race(() -> service.accept(userId(second.owner()), secondId),
				() -> service.accept(userId(second.owner()), secondId)))
				.containsOnlyNulls();
		assertThat(events(secondId)).hasSize(2);
		assertThat(memberService.countByPosition(teamPositionId(second))).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = {"closed", "expired", "archived", "blocked", "owner-sanction",
			"applicant-sanction", "applicant-inactive"})
	void acceptanceRechecksRestrictionsAfterWaitingForLocks(String restriction) throws Exception {
		Fixture fixture = fixture(1);
		UUID id = apply(fixture).id();
		try (var executor = Executors.newSingleThreadExecutor()) {
			Future<RuntimeException> future = transactionTemplate.execute(transaction -> {
				teamAccessService.requireOwnedForUpdate(userId(fixture.owner()), fixture.teamId(),
						List.of(profileId(fixture.applicant())));
				Future<RuntimeException> waiting = executor.submit(() -> capture(
						() -> service.accept(userId(fixture.owner()), id)));
				awaitLock();
				restrict(fixture, restriction);
				return waiting;
			});
			assertThat(future.get(20, TimeUnit.SECONDS)).isNotNull();
		}
		assertThat(repository.findById(id).orElseThrow().getStatus())
				.isEqualTo(TeamApplicationStatus.PENDING);
		assertThat(memberService.countByPosition(teamPositionId(fixture))).isZero();
		assertThat(events(id)).hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void publicationFailureRollsBackApplicationOrAcceptance(boolean accept) throws Exception {
		Fixture fixture = fixture(1);
		UUID id = accept ? apply(fixture).id() : null;
		long count = repository.count();
		OutboxEventService target = AopTestUtils.getUltimateTargetObject(publisher);
		doThrow(new IllegalStateException("Publication failed")).when(target)
				.publish(any(TeamApplicationChangedPayload.class));
		assertThatThrownBy(() -> {
			if (accept) {
				service.accept(userId(fixture.owner()), id);
			} else {
				apply(fixture);
			}
		}).isInstanceOf(IllegalStateException.class);
		assertThat(repository.count()).isEqualTo(count);
		assertThat(memberService.countByPosition(teamPositionId(fixture))).isZero();
		if (accept) {
			assertThat(repository.findById(id).orElseThrow().getStatus())
					.isEqualTo(TeamApplicationStatus.PENDING);
			assertThat(events(id)).hasSize(1);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"accept", "decline", "withdraw"})
	void outboxNotifiesBothSidesWithoutDuplicatesOrContacts(String action) throws Exception {
		Fixture fixture = fixture(1);
		UUID id = apply(fixture).id();
		OutboxEvent sent = events(id).getFirst();
		processEvents();
		handler.handle(sent);
		assertThat(notificationRepository.findByRecipientUserIdAndSourceEventId(
				userId(fixture.owner()), sent.getId())).isPresent();
		TestProfile actor = action.equals("withdraw") ? fixture.applicant() : fixture.owner();
		act(actor, id, action).andExpect(status().isOk());
		OutboxEvent resolved = events(id).stream()
				.filter(event -> !event.getId().equals(sent.getId())).findFirst().orElseThrow();
		processEvents();
		handler.handle(resolved);
		assertThat(notificationRepository.findAll()).hasSize(2);
		UUID recipient = userId(action.equals("withdraw") ? fixture.owner() : fixture.applicant());
		var notification = notificationRepository.findByRecipientUserIdAndSourceEventId(
				recipient, resolved.getId()).orElseThrow();
		NotificationType expected = switch (action) {
			case "accept" -> NotificationType.TEAM_APPLICATION_ACCEPTED;
			case "decline" -> NotificationType.TEAM_APPLICATION_DECLINED;
			default -> NotificationType.TEAM_APPLICATION_WITHDRAWN;
		};
		assertThat(notification.getType()).isEqualTo(expected);
		assertThat(notification.getPayload()).containsOnlyKeys(
				"applicationId", "teamId", "requestId", "positionId", "teamPositionId", "status");
		assertThat(events(id)).extracting(OutboxEvent::getStatus)
				.containsOnly(OutboxEventStatus.PROCESSED);
	}

	@Test
	void obsoleteEventIsSkippedButDecisionSurvivesClosedRequestAndDeadline() throws Exception {
		Fixture fixture = fixture(1);
		UUID id = apply(fixture).id();
		service.accept(userId(fixture.owner()), id);
		restrict(fixture, "closed");
		restrict(fixture, "expired");
		processEvents();
		assertThat(notificationRepository.findAll()).hasSize(1)
				.extracting(notification -> notification.getType())
				.containsExactly(NotificationType.TEAM_APPLICATION_ACCEPTED);
	}

	@ParameterizedTest
	@ValueSource(strings = {"closed", "expired", "archived", "blocked", "owner-sanction",
			"deleted-owner", "deleted-applicant"})
	void unavailableNewApplicationsDoNotProduceNotifications(String restriction) throws Exception {
		Fixture fixture = fixture(1);
		UUID id = apply(fixture).id();
		restrict(fixture, restriction);
		processEvents();
		assertThat(notificationRepository.findAll()).isEmpty();
		assertThat(events(id)).extracting(OutboxEvent::getStatus)
				.containsOnly(OutboxEventStatus.PROCESSED);
	}

	@Test
	void declineNotificationIsDeliveredForArchivedTeam() throws Exception {
		Fixture fixture = fixture(1);
		UUID id = apply(fixture).id();
		restrict(fixture, "archived");
		service.decline(userId(fixture.owner()), id);
		processEvents();
		assertThat(notificationRepository.findAll()).hasSize(1)
				.extracting(notification -> notification.getType())
				.containsExactly(NotificationType.TEAM_APPLICATION_DECLINED);
	}

	private Fixture fixture(int slots) throws Exception {
		TestProfile owner = profile("Owner");
		TestProfile applicant = profile("Applicant");
		UUID teamId = teamService.createForUser(userId(owner),
				new CreateTeamRequest("Team", null, "Build project", "Backend")).id();
		var request = request(owner, teamId, slots);
		return new Fixture(owner, applicant, teamId, request.id(),
				request.positions().getFirst().id());
	}

	private TeamRequestResponse request(TestProfile owner, UUID teamId, int slots) {
		return requestService.createForUser(userId(owner), teamId,
				new CreateRecruitmentRequest("Build a project", null,
						LocalDateTime.now().plusDays(2),
						Set.of(createPosition(owner, teamId, "Backend", slots))));
	}

	private TestProfile profile(String name) throws Exception {
		TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper)
				.createProfile(name);
		setStatus(profile, ProfileStatus.ACTIVE);
		return profile;
	}

	private UUID createPosition(TestProfile owner, UUID teamId, String name, int capacity) {
		return positionService.createForUser(userId(owner), teamId,
				new CreateTeamPositionRequest(name, null, capacity, Set.of())).id();
	}

	private UUID teamPositionId(Fixture fixture) {
		return requestService.findPosition(fixture.positionId()).getPosition().getId();
	}

	private TeamApplicationResponse apply(Fixture fixture) {
		return service.send(userId(fixture.applicant()), fixture.positionId(),
				new CreateTeamApplicationRequest(null));
	}

	private void invite(Fixture fixture, TestProfile invitee) {
		UUID id = invitationService.send(userId(fixture.owner()), fixture.teamId(),
				profileId(invitee), teamPositionId(fixture)).id();
		invitationService.accept(userId(invitee), id);
	}

	private ResultActions send(TestProfile applicant, UUID positionId, String message)
			throws Exception {
		return mockMvc.perform(applicant.owner().authorize(post(sendUrl(positionId)))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("message", message))));
	}

	private ResultActions act(TestProfile actor, UUID id, String action) throws Exception {
		return mockMvc.perform(actor.owner().authorize(post(base(id) + "/" + action)));
	}

	private ResultActions read(TestProfile actor, UUID id) throws Exception {
		return mockMvc.perform(actor.owner().authorize(get(base(id))));
	}

	private List<OutboxEvent> events(UUID id) {
		return eventRepository.findAll().stream()
				.filter(event -> event.getEventType() == OutboxEventType.TEAM_APPLICATION_CHANGED)
				.filter(event -> id.toString().equals(event.getPayload().get("applicationId").toString()))
				.toList();
	}

	private void processEvents() {
		String worker = "application-tests";
		LocalDateTime now = LocalDateTime.now().plusSeconds(1);
		for (UUID id : claimService.claimBatch(worker, 100, now)) {
			processor.process(id, worker, now);
		}
	}

	private void restrict(Fixture fixture, String restriction) {
		switch (restriction) {
			case "closed" -> requestService.closeForUser(
					userId(fixture.owner()), fixture.requestId());
			case "expired" -> jdbcTemplate.update(
					"UPDATE team_requests SET recruitment_deadline = ? WHERE id = ?",
					LocalDateTime.now().minusMinutes(1), fixture.requestId());
			case "archived" -> teamService.archiveForUser(
					userId(fixture.owner()), fixture.teamId());
			case "blocked" -> blockService.block(
					profileId(fixture.owner()), profileId(fixture.applicant()));
			case "owner-inactive" -> setStatus(fixture.owner(), ProfileStatus.DRAFT);
			case "applicant-inactive" -> setStatus(fixture.applicant(), ProfileStatus.DRAFT);
			case "owner-sanction" -> sanction(fixture.owner());
			case "applicant-sanction" -> sanction(fixture.applicant());
			case "deleted-owner" -> deletionService.deleteAccount(
					userId(fixture.owner()), "password123");
			case "deleted-applicant" -> deletionService.deleteAccount(
					userId(fixture.applicant()), "password123");
			default -> throw new IllegalArgumentException(restriction);
		}
	}

	private void setStatus(TestProfile profile, ProfileStatus status) {
		jdbcTemplate.update("UPDATE profiles SET status = ? WHERE id = ?",
				status.name(), profileId(profile));
	}

	private void sanction(TestProfile profile) {
		AccountSanction sanction = new AccountSanction();
		sanction.setTargetUserId(userId(profile));
		sanction.setTargetProfileId(profileId(profile));
		sanction.setType(AccountSanctionType.TEMPORARY_SUSPENSION);
		sanction.setReason("Test");
		sanction.setExpiresAt(LocalDateTime.now().plusHours(1));
		sanction.setCreatedByUserId(UUID.randomUUID());
		sanctionRepository.saveAndFlush(sanction);
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
			List<RuntimeException> results = new ArrayList<>();
			for (var future : futures) {
				results.add(future.get(20, TimeUnit.SECONDS));
			}
			return results;
		}
	}

	private void assertOneSuccess(List<RuntimeException> results) {
		assertThat(results.stream().filter(result -> result == null).count()).isEqualTo(1);
	}

	private RuntimeException capture(Runnable action) {
		try {
			action.run();
			return null;
		} catch (RuntimeException exception) {
			return exception;
		}
	}

	private void awaitLock() {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			jdbcTemplate.execute("SELECT pg_stat_clear_snapshot()");
			Boolean waiting = jdbcTemplate.queryForObject("""
					SELECT EXISTS (SELECT 1 FROM pg_stat_activity
					WHERE wait_event_type = 'Lock' AND LOWER(query) LIKE '%from users%')
					""", Boolean.class);
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
		throw new AssertionError("Application did not wait for account locks");
	}

	private UUID userId(TestProfile profile) {
		return UUID.fromString(profile.owner().userId());
	}

	private UUID profileId(TestProfile profile) {
		return UUID.fromString(profile.id());
	}

	private String sendUrl(UUID positionId) {
		return "/api/team-request-positions/" + positionId + "/applications";
	}

	private String base(UUID id) {
		return "/api/team-applications/" + id;
	}

	private String ownerList(Fixture fixture) {
		return "/api/team-requests/" + fixture.requestId() + "/applications";
	}

	private record Fixture(
			TestProfile owner, TestProfile applicant, UUID teamId, UUID requestId, UUID positionId
	) {
	}
}
