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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.team.TeamAccessService;
import ru.itmo.nemat.weezzy.team.TeamService;
import ru.itmo.nemat.weezzy.team.application.dto.CreateTeamApplicationRequest;
import ru.itmo.nemat.weezzy.team.application.dto.TeamApplicationResponse;
import ru.itmo.nemat.weezzy.team.dto.CreateTeamRequest;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationService;
import ru.itmo.nemat.weezzy.team.position.TeamPositionService;
import ru.itmo.nemat.weezzy.team.position.dto.CreateTeamPositionRequest;
import ru.itmo.nemat.weezzy.team.request.TeamRequestService;
import ru.itmo.nemat.weezzy.team.request.dto.CreateRecruitmentRequest;
import ru.itmo.nemat.weezzy.team.request.dto.TeamRequestResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false", "app.lunch.lifecycle.enabled=false",
		"spring.jpa.open-in-view=false"
})
class TeamApplicationCooldownTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private TeamApplicationService service;
	@Autowired private TeamApplicationRepository repository;
	@Autowired private TeamService teams;
	@Autowired private TeamPositionService positions;
	@Autowired private TeamRequestService requests;
	@Autowired private TeamInvitationService invitations;
	@Autowired private TeamAccessService access;
	@Autowired private TransactionTemplate transactions;
	@MockitoBean private Clock clock;

	private LocalDateTime now;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@BeforeEach
	void setUp() {
		now = LocalDateTime.now().plusHours(1).withNano(0);
		setTime(now);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void directPostRejectsSamePositionAcrossPublicationsWithoutWrites(boolean anotherPublication)
			throws Exception {
		var fixture = fixture();
		var publication = publish(fixture, fixture.teamPositionId());
		decline(fixture, apply(fixture, fixture.positionId()).id());
		long applications = repository.count();
		long events = eventCount();
		UUID target = anotherPublication ? publication.positions().getFirst().id() : fixture.positionId();
		send(fixture.applicant(), target).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", containsString(now.plusHours(24).toString())));
		assertThat(repository.count()).isEqualTo(applications);
		assertThat(eventCount()).isEqualTo(events);
	}

	@ParameterizedTest
	@ValueSource(strings = {"closed", "expired"})
	void unavailablePublicationDoesNotEraseItsDecline(String state) throws Exception {
		var fixture = fixture();
		var other = publish(fixture, fixture.teamPositionId());
		decline(fixture, apply(fixture, fixture.positionId()).id());
		if (state.equals("closed")) {
			requests.closeForUser(userId(fixture.owner()), fixture.requestId());
		} else {
			jdbc.update("UPDATE team_requests SET recruitment_deadline = ? WHERE id = ?",
					LocalDateTime.now().minusHours(1), fixture.requestId());
		}
		send(fixture.applicant(), other.positions().getFirst().id()).andExpect(status().isConflict());
	}

	@ParameterizedTest
	@ValueSource(longs = {-1, 0, 1})
	void allowsReapplicationExactlyAtTwentyFourHours(long offsetNanos) throws Exception {
		var fixture = fixture();
		decline(fixture, apply(fixture, fixture.positionId()).id());
		setTime(now.plusHours(24).plusNanos(offsetNanos));
		if (offsetNanos < 0) {
			send(fixture.applicant(), fixture.positionId()).andExpect(status().isConflict());
		} else {
			send(fixture.applicant(), fixture.positionId()).andExpect(status().isCreated());
		}
	}

	@Test
	void latestDeclineAcrossPublicationsWinsAndRepeatDoesNotExtendIt() throws Exception {
		var fixture = fixture();
		var other = publish(fixture, fixture.teamPositionId());
		UUID first = apply(fixture, fixture.positionId()).id();
		UUID second = apply(fixture, other.positions().getFirst().id()).id();
		decline(fixture, first);
		setTime(now.plusHours(2));
		decline(fixture, second);
		setTime(now.plusHours(25));
		long events = eventCount();
		assertThat(service.decline(userId(fixture.owner()), second).resolvedAt())
				.isEqualTo(now.plusHours(2));
		assertThat(eventCount()).isEqualTo(events);
		send(fixture.applicant(), fixture.positionId()).andExpect(status().isConflict());
		setTime(now.plusHours(26));
		send(fixture.applicant(), fixture.positionId()).andExpect(status().isCreated());
	}

	@Test
	void otherPositionsTeamsAndApplicantsRemainIndependent() throws Exception {
		var fixture = fixture();
		decline(fixture, apply(fixture, fixture.positionId()).id());
		UUID otherPosition = position(fixture.owner(), fixture.teamId());
		var otherPublication = publish(fixture, otherPosition);
		send(fixture.applicant(), otherPublication.positions().getFirst().id())
				.andExpect(status().isCreated());
		send(profile(), fixture.positionId()).andExpect(status().isCreated());
		var otherTeam = fixture();
		send(fixture.applicant(), otherTeam.positionId()).andExpect(status().isCreated());
	}

	@Test
	void voluntaryWithdrawalAloneAllowsImmediateReapplication() throws Exception {
		var fixture = fixture();
		service.withdraw(userId(fixture.applicant()), apply(fixture, fixture.positionId()).id());
		send(fixture.applicant(), fixture.positionId()).andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = {"accept", "withdraw", "decline"})
	void existingPendingCanStillBeResolvedAfterDeclineElsewhere(String decision) throws Exception {
		var fixture = fixture();
		var other = publish(fixture, fixture.teamPositionId());
		UUID first = apply(fixture, fixture.positionId()).id();
		UUID pending = apply(fixture, other.positions().getFirst().id()).id();
		decline(fixture, first);
		setTime(now.plusHours(1));
		TeamApplicationResponse result = switch (decision) {
			case "accept" -> service.accept(userId(fixture.owner()), pending);
			case "withdraw" -> service.withdraw(userId(fixture.applicant()), pending);
			case "decline" -> service.decline(userId(fixture.owner()), pending);
			default -> throw new IllegalArgumentException(decision);
		};
		assertThat(result.status()).isNotEqualTo(TeamApplicationStatus.PENDING);
		if (decision.equals("withdraw")) {
			send(fixture.applicant(), other.positions().getFirst().id()).andExpect(status().isConflict());
			assertThat(repository.findLastDeclinedAt(profileId(fixture.applicant()), fixture.teamPositionId()))
					.contains(now);
		}
		if (decision.equals("decline")) {
			assertThat(repository.findLastDeclinedAt(profileId(fixture.applicant()), fixture.teamPositionId()))
					.contains(now.plusHours(1));
		}
	}

	@Test
	void directInvitationStillAddsCandidateToDeclinedPosition() throws Exception {
		var fixture = fixture();
		decline(fixture, apply(fixture, fixture.positionId()).id());
		UUID invitation = invitations.send(userId(fixture.owner()), fixture.teamId(),
				profileId(fixture.applicant()), fixture.teamPositionId()).id();
		invitations.accept(userId(fixture.applicant()), invitation);
		assertThat(jdbc.queryForObject("""
				SELECT position_id FROM team_members WHERE team_id = ? AND profile_id = ?
				""", UUID.class, fixture.teamId(), profileId(fixture.applicant())))
				.isEqualTo(fixture.teamPositionId());
	}

	@Test
	void sendingStillRequiresAuthentication() throws Exception {
		mockMvc.perform(post(sendUrl(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void waitingSendSeesCommittedDeclineButNotRolledBackDecline(boolean rollback) throws Exception {
		var fixture = fixture();
		UUID first = apply(fixture, fixture.positionId()).id();
		UUID other = publish(fixture, fixture.teamPositionId()).positions().getFirst().id();
		long events = eventCount();
		try (var executor = Executors.newSingleThreadExecutor()) {
			Future<RuntimeException> future = transactions.execute(transaction -> {
				decline(fixture, first);
				Future<RuntimeException> waiting = executor.submit(() -> capture(() -> apply(fixture, other)));
				awaitAccountLock();
				if (rollback) {
					transaction.setRollbackOnly();
				}
				return waiting;
			});
			RuntimeException failure = future.get(20, TimeUnit.SECONDS);
			if (rollback) {
				assertThat(failure).isNull();
			} else {
				assertThat(failure).isInstanceOf(TeamApplicationCooldownException.class);
			}
		}
		assertThat(repository.findById(first).orElseThrow().getStatus()).isEqualTo(
				rollback ? TeamApplicationStatus.PENDING : TeamApplicationStatus.DECLINED);
		assertThat(repository.existsByPositionIdAndApplicantProfileIdAndStatus(
				other, profileId(fixture.applicant()), TeamApplicationStatus.PENDING)).isEqualTo(rollback);
		assertThat(eventCount()).isEqualTo(events + 1);
	}

	@Test
	void sendCommittedBeforeDeclineRemainsPendingAndSubsequentSendIsBlocked() throws Exception {
		var fixture = fixture();
		UUID first = apply(fixture, fixture.positionId()).id();
		UUID other = publish(fixture, fixture.teamPositionId()).positions().getFirst().id();
		try (var executor = Executors.newSingleThreadExecutor()) {
			Future<RuntimeException> future = transactions.execute(transaction -> {
				apply(fixture, other);
				Future<RuntimeException> waiting = executor.submit(() -> capture(() -> decline(fixture, first)));
				awaitAccountLock();
				return waiting;
			});
			assertThat(future.get(20, TimeUnit.SECONDS)).isNull();
		}
		assertThat(repository.existsByPositionIdAndApplicantProfileIdAndStatus(
				other, profileId(fixture.applicant()), TeamApplicationStatus.PENDING)).isTrue();
		send(fixture.applicant(), fixture.positionId()).andExpect(status().isConflict());
	}

	@Test
	void evaluatesCurrentTimeAfterWaitingForLocks() throws Exception {
		var fixture = fixture();
		decline(fixture, apply(fixture, fixture.positionId()).id());
		setTime(now.plusHours(24).minusSeconds(1));
		try (var executor = Executors.newSingleThreadExecutor()) {
			Future<RuntimeException> future = transactions.execute(transaction -> {
				access.requireOwnedForUpdate(userId(fixture.owner()), fixture.teamId(),
						List.of(profileId(fixture.applicant())));
				Future<RuntimeException> waiting = executor.submit(() -> capture(
						() -> apply(fixture, fixture.positionId())));
				awaitAccountLock();
				setTime(now.plusHours(24));
				return waiting;
			});
			assertThat(future.get(20, TimeUnit.SECONDS)).isNull();
		}
	}

	private Fixture fixture() throws Exception {
		TestProfile owner = profile();
		TestProfile applicant = profile();
		UUID team = teams.createForUser(userId(owner),
				new CreateTeamRequest("Team", null, "Project", "Founder")).id();
		UUID position = position(owner, team);
		var request = requests.createForUser(userId(owner), team, recruitment(position));
		return new Fixture(owner, applicant, team, position, request.id(), request.positions().getFirst().id());
	}

	private UUID position(TestProfile owner, UUID team) {
		return positions.createForUser(userId(owner), team,
				new CreateTeamPositionRequest("Backend", null, 2, Set.of())).id();
	}

	private TeamRequestResponse publish(Fixture fixture, UUID position) {
		return requests.createForUser(userId(fixture.owner()), fixture.teamId(), recruitment(position));
	}

	private CreateRecruitmentRequest recruitment(UUID position) {
		return new CreateRecruitmentRequest("Recruitment", null,
				LocalDateTime.now().plusDays(4), Set.of(position));
	}

	private TestProfile profile() throws Exception {
		var result = AuthenticatedTestUser.register(mockMvc, objectMapper).createProfile("User");
		jdbc.update("UPDATE profiles SET status = 'ACTIVE' WHERE id = ?", profileId(result));
		return result;
	}

	private TeamApplicationResponse apply(Fixture fixture, UUID position) {
		return service.send(userId(fixture.applicant()), position, new CreateTeamApplicationRequest(null));
	}

	private void decline(Fixture fixture, UUID application) {
		service.decline(userId(fixture.owner()), application);
	}

	private ResultActions send(TestProfile applicant, UUID position) throws Exception {
		return mockMvc.perform(applicant.owner().authorize(post(sendUrl(position)))
				.contentType(MediaType.APPLICATION_JSON).content("{}"));
	}

	private String sendUrl(UUID position) {
		return "/api/team-request-positions/" + position + "/applications";
	}

	private void setTime(LocalDateTime time) {
		ZoneId zone = ZoneId.systemDefault();
		when(clock.getZone()).thenReturn(zone);
		when(clock.instant()).thenReturn(time.atZone(zone).toInstant());
	}

	private long eventCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events", Long.class);
	}

	private RuntimeException capture(Runnable action) {
		try {
			action.run();
			return null;
		} catch (RuntimeException exception) {
			return exception;
		}
	}

	private void awaitAccountLock() {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			jdbc.execute("SELECT pg_stat_clear_snapshot()");
			if (Boolean.TRUE.equals(jdbc.queryForObject("""
					SELECT EXISTS (SELECT 1 FROM pg_stat_activity
					WHERE datname = current_database() AND pid <> pg_backend_pid()
					  AND wait_event_type = 'Lock' AND LOWER(query) LIKE '%from users%')
					""", Boolean.class))) {
				return;
			}
			try {
				Thread.sleep(20);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
		}
		throw new AssertionError("Operation did not wait for account locks");
	}

	private UUID userId(TestProfile profile) {
		return UUID.fromString(profile.owner().userId());
	}

	private UUID profileId(TestProfile profile) {
		return UUID.fromString(profile.id());
	}

	private record Fixture(TestProfile owner, TestProfile applicant, UUID teamId,
			UUID teamPositionId, UUID requestId, UUID positionId) {
	}
}
