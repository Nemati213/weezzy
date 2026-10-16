package ru.itmo.nemat.weezzy.team.request;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.skill.Skill;
import ru.itmo.nemat.weezzy.skill.SkillRepository;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser;
import ru.itmo.nemat.weezzy.support.AuthenticatedTestUser.TestProfile;
import ru.itmo.nemat.weezzy.team.TeamNotActiveException;
import ru.itmo.nemat.weezzy.team.TeamAccessService;
import ru.itmo.nemat.weezzy.team.TeamRepository;
import ru.itmo.nemat.weezzy.team.TeamService;
import ru.itmo.nemat.weezzy.team.dto.CreateTeamRequest;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationService;
import ru.itmo.nemat.weezzy.team.member.TeamMemberRepository;
import ru.itmo.nemat.weezzy.team.request.dto.CreateRecruitmentRequest;
import ru.itmo.nemat.weezzy.team.request.dto.UpdateRecruitmentRequest;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPositionRepository;
import ru.itmo.nemat.weezzy.team.request.position.skill.TeamRequestPositionSkill;
import ru.itmo.nemat.weezzy.team.request.position.skill.TeamRequestPositionSkillRepository;
import ru.itmo.nemat.weezzy.user.AccountTemporarilySuspendedException;
import ru.itmo.nemat.weezzy.user.UserRepository;
import ru.itmo.nemat.weezzy.user.accountdeletion.AccountDeletionService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class TeamRequestControllerTests {
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
	private TeamRequestService service;
	@Autowired
	private TeamRequestRepository requestRepository;
	@Autowired
	private TeamRequestPositionRepository positionRepository;
	@MockitoSpyBean
	private TeamRequestPositionSkillRepository positionSkillRepository;
	@Autowired
	private TeamService teamService;
	@Autowired
	private TeamAccessService teamAccessService;
	@Autowired
	private TeamRepository teamRepository;
	@Autowired
	private TeamMemberRepository memberRepository;
	@Autowired
	private TeamInvitationService invitationService;
	@Autowired
	private SkillRepository skillRepository;
	@Autowired
	private ProfileBlockService blockService;
	@Autowired
	private AccountSanctionRepository sanctionRepository;
	@Autowired
	private AccountDeletionService deletionService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void ownerCreatesRequestWithPositionsAndCatalogSkillsWithoutExposingContacts()
			throws Exception {
		Fixture fixture = fixture();
		Map<String, Object> body = body(fixture);
		body.put("title", "  Hackathon  ");
		body.put("description", "  Build a service  ");
		body.put("positions", List.of(position("  Backend  ", 1, fixture.skillId()),
				position("Designer", 1, fixture.skillId())));
		String response = create(fixture.owner(), fixture.teamId(), body)
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.teamId").value(fixture.teamId().toString()))
				.andExpect(jsonPath("$.title").value("Hackathon"))
				.andExpect(jsonPath("$.description").value("Build a service"))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.targetTeamSize").value(3))
				.andExpect(jsonPath("$.positions.length()").value(2))
				.andExpect(jsonPath("$.positions[0].name").value("Backend"))
				.andExpect(jsonPath("$.positions[0].slotsCount").value(1))
				.andExpect(jsonPath("$.positions[0].skills[0].id")
						.value(fixture.skillId().toString()))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty())
				.andExpect(jsonPath("$.userId").doesNotExist())
				.andExpect(jsonPath("$.email").doesNotExist())
				.andExpect(jsonPath("$.telegram").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		UUID id = id(response);
		assertThat(positionRepository.countByRequestId(id)).isEqualTo(2);
		assertThat(memberRepository.countByTeamId(fixture.teamId())).isEqualTo(1);
		mockMvc.perform(fixture.viewer().owner().authorize(get(base(id))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.positions[1].skills[0].id")
						.value(fixture.skillId().toString()));
		AuthenticatedTestUser noProfile = AuthenticatedTestUser.register(mockMvc, objectMapper);
		mockMvc.perform(noProfile.authorize(get(base(id)))).andExpect(status().isOk());
	}

	@Test
	void updateChangesOnlyRequestFieldsAndRetainsPositionIdentity() throws Exception {
		Fixture fixture = fixture();
		UUID id = createId(fixture);
		JsonNode before = read(fixture.owner(), id);
		Map<String, Object> update = updateBody();
		update.put("title", "  New title  ");
		update.put("description", null);
		update.put("status", "CLOSED");
		update.put("teamId", UUID.randomUUID());
		update.put("positions", List.of());
		String response = update(fixture.owner(), id, update)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("New title"))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.teamId").value(fixture.teamId().toString()))
				.andExpect(jsonPath("$.description").isEmpty())
				.andReturn().getResponse().getContentAsString();
		JsonNode after = objectMapper.readTree(response);
		assertThat(after.path("positions")).isEqualTo(before.path("positions"));
		assertThat(after.path("createdAt")).isEqualTo(before.path("createdAt"));
		assertThat(after.path("updatedAt").asText())
				.isNotEqualTo(before.path("updatedAt").asText());
	}

	@Test
	void closeIsIdempotentAndOwnerCanReadHistoryWithInactiveProfile() throws Exception {
		Fixture fixture = fixture();
		UUID id = createId(fixture);
		setStatus(fixture.owner(), ProfileStatus.HIDDEN);
		String first = close(fixture.owner(), id).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"))
				.andReturn().getResponse().getContentAsString();
		String second = close(fixture.owner(), id).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(objectMapper.readTree(second)).isEqualTo(objectMapper.readTree(first));
		mockMvc.perform(fixture.owner().owner().authorize(get(base(id))))
				.andExpect(status().isOk());
		mockMvc.perform(fixture.viewer().owner().authorize(get(base(id))))
				.andExpect(status().isNotFound());
		update(fixture.owner(), id, updateBody()).andExpect(status().isConflict());
	}

	@Test
	void ownListSupportsStatusPaginationAndStableOrderWithoutForeignRequests() throws Exception {
		Fixture fixture = fixture();
		UUID first = createId(fixture);
		UUID second = createId(fixture);
		UUID closed = createId(fixture);
		close(fixture.owner(), closed).andExpect(status().isOk());
		Fixture foreign = fixture();
		createId(foreign);
		jdbcTemplate.update("UPDATE team_requests SET created_at = ? WHERE team_id = ?",
				LocalDateTime.now().minusDays(1), fixture.teamId());
		List<String> openIds = List.of(first.toString(), second.toString()).stream()
				.sorted().toList();
		mockMvc.perform(fixture.owner().owner().authorize(get(list(fixture.teamId()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3));
		for (int page = 0; page < 2; page++) {
			mockMvc.perform(fixture.owner().owner().authorize(get(list(fixture.teamId())))
						.param("status", "OPEN").param("size", "1")
						.param("page", String.valueOf(page)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElements").value(2))
					.andExpect(jsonPath("$.content[0].id").value(openIds.get(page)))
					.andExpect(jsonPath("$.content[0].positions.length()").value(2));
		}
		mockMvc.perform(fixture.owner().owner().authorize(get(list(fixture.teamId())))
					.param("status", "CLOSED"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(closed.toString()));
	}

	@Test
	void allEndpointsRequireAuthentication() throws Exception {
		UUID teamId = UUID.randomUUID();
		UUID requestId = UUID.randomUUID();
		for (MockHttpServletRequestBuilder request : List.of(
				post("/api/teams/" + teamId + "/requests").content("{}"),
				get(base(requestId)), get(list(teamId)),
				put(base(requestId)).content("{}"), post(base(requestId) + "/close"))) {
			mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON))
					.andExpect(status().isUnauthorized());
		}
	}

	@Test
	void membersAndOutsidersCannotCreateEditCloseOrReadOwnerList() throws Exception {
		Fixture fixture = fixture();
		UUID id = createId(fixture);
		for (boolean join : List.of(false, true)) {
			if (join) {
				UUID invitation = invitationService.send(userId(fixture.owner()), fixture.teamId(),
						UUID.fromString(fixture.viewer().id())).id();
				invitationService.accept(userId(fixture.viewer()), invitation);
			}
			create(fixture.viewer(), fixture.teamId(), body(fixture))
					.andExpect(status().isForbidden());
			update(fixture.viewer(), id, updateBody()).andExpect(status().isForbidden());
			close(fixture.viewer(), id).andExpect(status().isForbidden());
			mockMvc.perform(fixture.viewer().owner().authorize(get(list(fixture.teamId()))))
					.andExpect(status().isForbidden());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"blank-title", "long-title", "long-description", "past-deadline",
			"missing-deadline", "zero-size", "no-positions", "null-position", "blank-position",
			"long-position", "zero-slots", "null-skills", "null-skill", "overflow-slots"})
	void invalidCreatePayloadsAreRejectedWithoutPartialRows(String invalid) throws Exception {
		Fixture fixture = fixture();
		Map<String, Object> body = body(fixture);
		switch (invalid) {
			case "blank-title" -> body.put("title", "   ");
			case "long-title" -> body.put("title", "x".repeat(121));
			case "long-description" -> body.put("description", "x".repeat(2001));
			case "past-deadline" -> body.put("recruitmentDeadline",
					LocalDateTime.now().minusMinutes(1));
			case "missing-deadline" -> body.remove("recruitmentDeadline");
			case "zero-size" -> body.put("targetTeamSize", 0);
			case "no-positions" -> body.put("positions", List.of());
			case "null-position" -> body.put("positions", Arrays.asList((Object) null));
			case "blank-position" -> body.put("positions",
					List.of(position("   ", 2, fixture.skillId())));
			case "long-position" -> body.put("positions",
					List.of(position("x".repeat(121), 2, fixture.skillId())));
			case "zero-slots" -> body.put("positions",
					List.of(position("Backend", 0, fixture.skillId())));
			case "null-skills", "null-skill" -> {
				Map<String, Object> position = position("Backend", 2, fixture.skillId());
				position.put("skillIds", invalid.equals("null-skills")
						? null : Arrays.asList((Object) null));
				body.put("positions", List.of(position));
			}
			case "overflow-slots" -> body.put("positions", List.of(
					position("Backend", Integer.MAX_VALUE, fixture.skillId()),
					position("Designer", Integer.MAX_VALUE, fixture.skillId()),
					position("Tester", 4, fixture.skillId())));
			default -> throw new IllegalArgumentException(invalid);
		}
		long count = requestRepository.count();
		create(fixture.owner(), fixture.teamId(), body).andExpect(status().isBadRequest());
		assertThat(requestRepository.count()).isEqualTo(count);
	}

	@Test
	void invalidSizeMissingSkillAndMissingResourcesAreHandled() throws Exception {
		Fixture fixture = fixture();
		Map<String, Object> body = body(fixture);
		body.put("targetTeamSize", 4);
		create(fixture.owner(), fixture.teamId(), body).andExpect(status().isBadRequest());
		body = body(fixture);
		body.put("positions", List.of(position("Backend", 2, UUID.randomUUID())));
		create(fixture.owner(), fixture.teamId(), body).andExpect(status().isNotFound());
		create(fixture.owner(), UUID.randomUUID(), body(fixture))
				.andExpect(status().isNotFound());
		UUID missing = UUID.randomUUID();
		mockMvc.perform(fixture.owner().owner().authorize(get(base(missing))))
				.andExpect(status().isNotFound());
		update(fixture.owner(), missing, updateBody()).andExpect(status().isNotFound());
		close(fixture.owner(), missing).andExpect(status().isNotFound());
		mockMvc.perform(fixture.owner().owner().authorize(get(list(UUID.randomUUID()))))
				.andExpect(status().isNotFound());
	}

	@ParameterizedTest
	@ValueSource(strings = {"page=-1", "size=0", "size=101", "status=UNKNOWN"})
	void listValidatesPaginationAndStatus(String parameter) throws Exception {
		Fixture fixture = fixture();
		mockMvc.perform(fixture.owner().owner().authorize(
				get(list(fixture.teamId()) + "?" + parameter)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateValidatesDeadlineSizeAndRequiredFields() throws Exception {
		Fixture fixture = fixture();
		UUID id = createId(fixture);
		Map<String, Object> update = updateBody();
		update.put("recruitmentDeadline", LocalDateTime.now().minusMinutes(1));
		update(fixture.owner(), id, update).andExpect(status().isBadRequest());
		update = updateBody();
		update.put("targetTeamSize", 4);
		update(fixture.owner(), id, update).andExpect(status().isBadRequest());
		update(fixture.owner(), id, Map.of("title", "Missing fields"))
				.andExpect(status().isBadRequest());
		assertThat(read(fixture.owner(), id).path("title").asText()).isEqualTo("Hackathon");
	}

	@ParameterizedTest
	@EnumSource(value = ProfileStatus.class, names = {"DRAFT", "HIDDEN", "DELETED"})
	void inactiveOwnerCannotPublishOrEditAndIsHiddenFromOthers(ProfileStatus profileStatus)
			throws Exception {
		Fixture fixture = fixture();
		UUID id = createId(fixture);
		setStatus(fixture.owner(), profileStatus);
		create(fixture.owner(), fixture.teamId(), body(fixture))
				.andExpect(status().isConflict());
		update(fixture.owner(), id, updateBody()).andExpect(status().isConflict());
		mockMvc.perform(fixture.viewer().owner().authorize(get(base(id))))
				.andExpect(status().isNotFound());
		close(fixture.owner(), id).andExpect(status().isOk());
	}

	@Test
	void expiredAndArchivedRequestsAreVisibleOnlyToOwnerAndCanBeClosed() throws Exception {
		Fixture fixture = fixture();
		UUID id = createId(fixture);
		jdbcTemplate.update("UPDATE team_requests SET recruitment_deadline = ? WHERE id = ?",
				LocalDateTime.now().minusMinutes(1), id);
		mockMvc.perform(fixture.viewer().owner().authorize(get(base(id))))
				.andExpect(status().isNotFound());
		mockMvc.perform(fixture.owner().owner().authorize(get(base(id))))
				.andExpect(status().isOk());
		update(fixture.owner(), id, updateBody()).andExpect(status().isOk());
		mockMvc.perform(fixture.viewer().owner().authorize(get(base(id))))
				.andExpect(status().isOk());
		teamService.archiveForUser(userId(fixture.owner()), fixture.teamId());
		mockMvc.perform(fixture.viewer().owner().authorize(get(base(id))))
				.andExpect(status().isNotFound());
		mockMvc.perform(fixture.owner().owner().authorize(get(list(fixture.teamId()))))
				.andExpect(status().isOk());
		create(fixture.owner(), fixture.teamId(), body(fixture))
				.andExpect(status().isConflict());
		update(fixture.owner(), id, updateBody()).andExpect(status().isConflict());
		close(fixture.owner(), id).andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void blocksInEitherDirectionHidePublicRequest(boolean ownerBlocks) throws Exception {
		Fixture fixture = fixture();
		UUID id = createId(fixture);
		UUID ownerId = UUID.fromString(fixture.owner().id());
		UUID viewerId = UUID.fromString(fixture.viewer().id());
		blockService.block(ownerBlocks ? ownerId : viewerId,
				ownerBlocks ? viewerId : ownerId);
		mockMvc.perform(fixture.viewer().owner().authorize(get(base(id))))
				.andExpect(status().isNotFound());
		mockMvc.perform(fixture.owner().owner().authorize(get(base(id))))
				.andExpect(status().isOk());
	}

	@Test
	void sanctionsAndAccountDeletionHidePublicRequests() throws Exception {
		Fixture banned = fixture();
		UUID bannedId = createId(banned);
		sanction(banned.owner());
		mockMvc.perform(banned.viewer().owner().authorize(get(base(bannedId))))
				.andExpect(status().isNotFound());
		close(banned.owner(), bannedId).andExpect(status().isForbidden());
		Fixture deleted = fixture();
		UUID deletedId = createId(deleted);
		deletionService.deleteAccount(userId(deleted.owner()), "password123");
		mockMvc.perform(deleted.viewer().owner().authorize(get(base(deletedId))))
				.andExpect(status().isNotFound());
	}

	@Test
	void persistenceFailureRollsBackRequestPositionsAndSkillLinks() throws Exception {
		Fixture fixture = fixture();
		long requests = requestRepository.count();
		long positions = positionRepository.count();
		long skills = positionSkillRepository.count();
		doThrow(new DataIntegrityViolationException("Test save failure"))
				.when(positionSkillRepository).saveAll(anyList());
		assertThatThrownBy(() -> create(fixture.owner(), fixture.teamId(), body(fixture)))
				.hasRootCauseInstanceOf(DataIntegrityViolationException.class);
		assertThat(requestRepository.count()).isEqualTo(requests);
		assertThat(positionRepository.count()).isEqualTo(positions);
		assertThat(positionSkillRepository.count()).isEqualTo(skills);
	}

	@Test
	void positionsCanHaveEmptySkillSetsAndBlankDescriptionsAreNormalized() throws Exception {
		Fixture fixture = fixture();
		Map<String, Object> body = body(fixture);
		Map<String, Object> position = position("Backend", 2, fixture.skillId());
		position.put("skillIds", List.of());
		position.put("description", "   ");
		body.put("description", "   ");
		body.put("positions", List.of(position));
		create(fixture.owner(), fixture.teamId(), body)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.description").isEmpty())
				.andExpect(jsonPath("$.positions[0].description").isEmpty())
				.andExpect(jsonPath("$.positions[0].skills.length()").value(0));
	}

	@Test
	void concurrentCloseAndUpdateCannotReopenRequest() throws Exception {
		Fixture fixture = fixture();
		UUID id = createId(fixture);
		UpdateRecruitmentRequest update = objectMapper.convertValue(updateBody(),
				UpdateRecruitmentRequest.class);
		List<RuntimeException> outcomes = race(
				() -> service.closeForUser(userId(fixture.owner()), id),
				() -> service.updateForUser(userId(fixture.owner()), id, update));
		assertThat(outcomes.getFirst()).isNull();
		if (outcomes.getLast() != null) {
			assertThat(outcomes.getLast()).isInstanceOf(TeamRequestClosedException.class);
		}
		assertThat(requestRepository.findById(id).orElseThrow().getStatus())
				.isEqualTo(TeamRequestStatus.CLOSED);
	}

	@Test
	void concurrentArchiveAndCreationAreSerialized() throws Exception {
		Fixture fixture = fixture();
		CreateRecruitmentRequest request = objectMapper.convertValue(body(fixture),
				CreateRecruitmentRequest.class);
		List<RuntimeException> outcomes = race(
				() -> teamService.archiveForUser(userId(fixture.owner()), fixture.teamId()),
				() -> service.createForUser(userId(fixture.owner()), fixture.teamId(), request));
		assertThat(outcomes.getFirst()).isNull();
		if (outcomes.getLast() != null) {
			assertThat(outcomes.getLast()).isInstanceOf(TeamNotActiveException.class);
		}
		assertThat(teamRepository.findById(fixture.teamId()).orElseThrow().getStatus().name())
				.isEqualTo("ARCHIVED");
		assertThat(requestRepository.findByTeamId(fixture.teamId(),
				org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
				.isEqualTo(outcomes.getLast() == null ? 1 : 0);
	}

	@Test
	void creationWaitingForMembershipChangeUsesCommittedMemberCount() throws Exception {
		Fixture fixture = fixture();
		UUID invitation = invitationService.send(userId(fixture.owner()), fixture.teamId(),
				UUID.fromString(fixture.viewer().id())).id();
		CreateRecruitmentRequest request = objectMapper.convertValue(body(fixture),
				CreateRecruitmentRequest.class);
		try (var executor = Executors.newSingleThreadExecutor()) {
			Future<RuntimeException> future = transactionTemplate.execute(transaction -> {
				teamAccessService.requireOwnedForUpdate(userId(fixture.owner()), fixture.teamId(),
						List.of(UUID.fromString(fixture.viewer().id())));
				Future<RuntimeException> pending = executor.submit(() -> capture(() ->
						service.createForUser(userId(fixture.owner()), fixture.teamId(), request)));
				awaitLock("from users");
				invitationService.accept(userId(fixture.viewer()), invitation);
				return pending;
			});
			assertThat(future.get(20, TimeUnit.SECONDS))
					.isInstanceOf(InvalidRecruitmentSizeException.class);
		}
		Map<String, Object> adjusted = body(fixture);
		adjusted.put("targetTeamSize", 4);
		UUID id = id(create(fixture.owner(), fixture.teamId(), adjusted)
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		update(fixture.owner(), id, updateBody()).andExpect(status().isBadRequest());
		Map<String, Object> update = updateBody();
		update.put("targetTeamSize", 4);
		update(fixture.owner(), id, update).andExpect(status().isOk());
	}

	@Test
	void creationWaitingForSanctionCannotPublish() throws Exception {
		Fixture fixture = fixture();
		CreateRecruitmentRequest request = objectMapper.convertValue(body(fixture),
				CreateRecruitmentRequest.class);
		try (var executor = Executors.newSingleThreadExecutor()) {
			Future<RuntimeException> future = transactionTemplate.execute(transaction -> {
				userRepository.findByIdForUpdate(userId(fixture.owner())).orElseThrow();
				Future<RuntimeException> pending = executor.submit(() -> capture(() ->
						service.createForUser(userId(fixture.owner()), fixture.teamId(), request)));
				awaitLock("from users");
				sanction(fixture.owner());
				return pending;
			});
			assertThat(future.get(20, TimeUnit.SECONDS))
					.isInstanceOf(AccountTemporarilySuspendedException.class);
		}
		assertThat(requestRepository.findByTeamId(fixture.teamId(),
				org.springframework.data.domain.Pageable.unpaged())).isEmpty();
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
		throw new AssertionError("Recruitment operation did not wait for " + table + " lock");
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
			return Arrays.asList(futures.getFirst().get(20, TimeUnit.SECONDS),
					futures.getLast().get(20, TimeUnit.SECONDS));
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
		TestProfile owner = profile("Owner");
		TestProfile viewer = profile("Viewer");
		UUID teamId = teamService.createForUser(userId(owner),
				new CreateTeamRequest("Team", null)).id();
		Skill skill = new Skill();
		skill.setName("Java " + UUID.randomUUID());
		UUID skillId = skillRepository.saveAndFlush(skill).getId();
		return new Fixture(owner, viewer, teamId, skillId);
	}

	private TestProfile profile(String name) throws Exception {
		TestProfile profile = AuthenticatedTestUser.register(mockMvc, objectMapper)
				.createProfile(name);
		setStatus(profile, ProfileStatus.ACTIVE);
		return profile;
	}

	private void setStatus(TestProfile profile, ProfileStatus status) {
		jdbcTemplate.update("UPDATE profiles SET status = ? WHERE id = ?",
				status.name(), UUID.fromString(profile.id()));
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

	private Map<String, Object> body(Fixture fixture) {
		Map<String, Object> body = updateBody();
		body.put("positions", List.of(position("Backend", 1, fixture.skillId()),
				position("Designer", 1, fixture.skillId())));
		return body;
	}

	private Map<String, Object> updateBody() {
		Map<String, Object> body = new HashMap<>();
		body.put("title", "Hackathon");
		body.put("description", "Build a service");
		body.put("recruitmentDeadline", LocalDateTime.now().plusDays(7).withNano(0));
		body.put("targetTeamSize", 3);
		return body;
	}

	private Map<String, Object> position(String name, int slots, UUID skillId) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("description", "Tasks");
		body.put("slotsCount", slots);
		body.put("skillIds", List.of(skillId));
		return body;
	}

	private ResultActions create(TestProfile owner, UUID teamId, Map<String, Object> body)
			throws Exception {
		return mockMvc.perform(owner.owner().authorize(post("/api/teams/" + teamId + "/requests"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	private UUID createId(Fixture fixture) throws Exception {
		return id(create(fixture.owner(), fixture.teamId(), body(fixture))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private ResultActions update(TestProfile owner, UUID id, Map<String, Object> body)
			throws Exception {
		return mockMvc.perform(owner.owner().authorize(put(base(id)))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	private ResultActions close(TestProfile owner, UUID id) throws Exception {
		return mockMvc.perform(owner.owner().authorize(post(base(id) + "/close")));
	}

	private JsonNode read(TestProfile viewer, UUID id) throws Exception {
		return objectMapper.readTree(mockMvc.perform(viewer.owner().authorize(get(base(id))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private UUID id(String response) {
		return UUID.fromString(objectMapper.readTree(response).path("id").asText());
	}

	private UUID userId(TestProfile profile) {
		return UUID.fromString(profile.owner().userId());
	}

	private String base(UUID id) {
		return "/api/team-requests/" + id;
	}

	private String list(UUID teamId) {
		return "/api/teams/" + teamId + "/requests/me";
	}

	private record Fixture(TestProfile owner, TestProfile viewer, UUID teamId, UUID skillId) {
	}
}
