package ru.itmo.nemat.weezzy.team.request;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.skill.Skill;
import ru.itmo.nemat.weezzy.skill.SkillRepository;
import ru.itmo.nemat.weezzy.team.Team;
import ru.itmo.nemat.weezzy.team.TeamRepository;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPosition;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPositionRepository;
import ru.itmo.nemat.weezzy.team.request.position.skill.TeamRequestPositionSkill;
import ru.itmo.nemat.weezzy.team.request.position.skill.TeamRequestPositionSkillId;
import ru.itmo.nemat.weezzy.team.request.position.skill.TeamRequestPositionSkillRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
		"app.lunch.matching.enabled=false",
		"app.lunch.lifecycle.enabled=false"
})
class TeamRequestRepositoryTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private TeamRequestRepository requestRepository;

	@Autowired
	private TeamRequestPositionRepository positionRepository;

	@Autowired
	private TeamRequestPositionSkillRepository positionSkillRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private ProfileRepository profileRepository;

	@Autowired
	private SkillRepository skillRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void requestPositionsAndCatalogSkillsCanBeSavedSeparately() {
		Team team = createTeam();
		TeamRequest request = createRequest(team);
		TeamRequestPosition backend = createPosition(request, "Backend");
		TeamRequestPosition designer = createPosition(request, "Designer");
		Skill java = createSkill("Java");
		Skill figma = createSkill("Figma");
		addSkill(backend, java);
		addSkill(designer, figma);

		TeamRequest found = requestRepository.findById(request.getId()).orElseThrow();
		assertThat(found.getTeam().getId()).isEqualTo(team.getId());
		assertThat(found.getTitle()).isEqualTo("Hackathon recruitment");
		assertThat(found.getDescription()).isEqualTo("A service for students");
		assertThat(found.getStatus()).isEqualTo(TeamRequestStatus.OPEN);
		assertThat(found.getTargetTeamSize()).isEqualTo(4);
		assertThat(found.getRecruitmentDeadline())
				.isEqualTo(request.getRecruitmentDeadline());
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
		assertThat(positionRepository.countByRequestId(request.getId())).isEqualTo(2);
		assertThat(positionRepository.findByRequestIdOrderByCreatedAtAscIdAsc(request.getId()))
				.extracting(TeamRequestPosition::getId)
				.containsExactly(backend.getId(), designer.getId());
		TeamRequestPosition foundPosition = positionRepository.findById(backend.getId())
				.orElseThrow();
		assertThat(foundPosition.getRequest().getId()).isEqualTo(request.getId());
		assertThat(foundPosition.getName()).isEqualTo("Backend");
		assertThat(foundPosition.getDescription()).isEqualTo("Build the product");
		assertThat(foundPosition.getSlotsCount()).isEqualTo(1);
		assertThat(foundPosition.getCreatedAt()).isNotNull();
		assertThat(foundPosition.getUpdatedAt()).isNotNull();
		assertThat(positionSkillRepository.findByPositionIdOrderBySkillIdAsc(backend.getId()))
				.extracting(link -> link.getSkill().getName()).containsExactly(java.getName());
		assertThat(positionSkillRepository
				.findByPositionIdInOrderByPositionIdAscSkillIdAsc(
						List.of(backend.getId(), designer.getId())
				))
				.extracting(link -> link.getSkill().getId())
				.containsExactlyInAnyOrder(java.getId(), figma.getId());
	}

	@Test
	void teamRequestsCanBePagedAndFilteredByStatus() {
		Team team = createTeam();
		TeamRequest first = createRequest(team);
		TeamRequest second = createRequest(team);
		TeamRequest closed = createRequest(team);
		closed.setStatus(TeamRequestStatus.CLOSED);
		requestRepository.saveAndFlush(closed);
		createRequest(createTeam());
		LocalDateTime sameTime = LocalDateTime.now().minusDays(1);
		jdbcTemplate.update("UPDATE team_requests SET created_at = ? WHERE team_id = ?",
				sameTime, team.getId());
		List<UUID> openIds = List.of(first.getId(), second.getId()).stream()
				.sorted(java.util.Comparator.comparing(UUID::toString)).toList();
		Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

		var page = requestRepository.findByTeamIdAndStatus(
				team.getId(), TeamRequestStatus.OPEN, PageRequest.of(0, 1, sort)
		);
		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.getContent()).extracting(TeamRequest::getId)
				.containsExactly(openIds.getFirst());
		assertThat(page.getContent().getFirst().getTeam().getName())
				.isEqualTo(team.getName());
		assertThat(requestRepository.findByTeamIdAndStatus(
				team.getId(), TeamRequestStatus.OPEN, PageRequest.of(1, 1, sort)
		).getContent()).extracting(TeamRequest::getId).containsExactly(openIds.getLast());
		assertThat(requestRepository.findByTeamId(team.getId(), PageRequest.of(0, 10)))
				.extracting(TeamRequest::getId)
				.containsExactlyInAnyOrder(first.getId(), second.getId(), closed.getId());
	}

	@Test
	void requestCanBeLocatedAndUpdatedUnderLock() {
		TeamRequest created = createRequest(createTeam());
		TeamRequest request = requestRepository.findById(created.getId()).orElseThrow();
		assertThat(requestRepository.findTeamId(request.getId()))
				.contains(request.getTeam().getId());
		assertThat(requestRepository.findTeamId(UUID.randomUUID())).isEmpty();

		transactionTemplate.executeWithoutResult(status -> {
			TeamRequest locked = requestRepository.findByIdForUpdate(request.getId())
					.orElseThrow();
			locked.setStatus(TeamRequestStatus.CLOSED);
		});

		TeamRequest found = requestRepository.findById(request.getId()).orElseThrow();
		assertThat(found.getStatus()).isEqualTo(TeamRequestStatus.CLOSED);
		assertThat(found.getCreatedAt()).isEqualTo(request.getCreatedAt());
		assertThat(found.getUpdatedAt()).isAfter(request.getUpdatedAt());
	}

	@Test
	void changingPositionUpdatesTimestampAndPreservesCreationTime() {
		TeamRequestPosition created = createPosition(createRequest(createTeam()), "Backend");
		TeamRequestPosition position = positionRepository.findById(created.getId())
				.orElseThrow();
		position.setSlotsCount(2);
		positionRepository.saveAndFlush(position);

		TeamRequestPosition found = positionRepository.findById(position.getId()).orElseThrow();
		assertThat(found.getSlotsCount()).isEqualTo(2);
		assertThat(found.getCreatedAt()).isEqualTo(position.getCreatedAt());
		assertThat(found.getUpdatedAt()).isAfter(position.getUpdatedAt());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	void blankRequestTitleAndPositionNameAreRejectedByDatabase(String text) {
		TeamRequest request = createRequest(createTeam());
		TeamRequestPosition position = createPosition(request, "Backend");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE team_requests SET title = ? WHERE id = ?", text, request.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE team_request_positions SET name = ? WHERE id = ?", text, position.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1})
	void nonPositiveTeamSizeAndSlotCountAreRejectedByDatabase(int count) {
		TeamRequest request = createRequest(createTeam());
		TeamRequestPosition position = createPosition(request, "Backend");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE team_requests SET target_team_size = ? WHERE id = ?",
				count, request.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE team_request_positions SET slots_count = ? WHERE id = ?",
				count, position.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void requestRequiresKnownStatusAndDeadline() {
		TeamRequest request = createRequest(createTeam());

		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE team_requests SET status = 'UNKNOWN' WHERE id = ?", request.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE team_requests SET recruitment_deadline = NULL WHERE id = ?",
				request.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void missingParentsAreRejectedByDatabase() {
		TeamRequest request = createRequest(createTeam());
		TeamRequestPosition position = createPosition(request, "Backend");
		Skill skill = createSkill("Java");
		addSkill(position, skill);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE team_requests SET team_id = ? WHERE id = ?",
				UUID.randomUUID(), request.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE team_request_positions SET request_id = ? WHERE id = ?",
				UUID.randomUUID(), position.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO team_request_position_skills (position_id, skill_id)
				VALUES (?, ?)
				""", UUID.randomUUID(), skill.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO team_request_position_skills (position_id, skill_id)
				VALUES (?, ?)
				""", position.getId(), UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sameSkillCannotBeLinkedTwiceToPositionButCanBeUsedInAnotherPosition() {
		TeamRequest request = createRequest(createTeam());
		TeamRequestPosition first = createPosition(request, "Backend");
		TeamRequestPosition second = createPosition(request, "Fullstack");
		Skill skill = createSkill("Java");
		addSkill(first, skill);
		addSkill(second, skill);

		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO team_request_position_skills (position_id, skill_id)
				VALUES (?, ?)
				""", first.getId(), skill.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(positionSkillRepository.findByPositionIdOrderBySkillIdAsc(second.getId()))
				.hasSize(1);
	}

	@Test
	void deletingRequestCascadesToPositionsAndLinksButPreservesTeamAndSkills() {
		TeamRequest request = createRequest(createTeam());
		TeamRequestPosition position = createPosition(request, "Backend");
		Skill skill = createSkill("Java");
		TeamRequestPositionSkill link = addSkill(position, skill);

		requestRepository.deleteById(request.getId());

		assertThat(requestRepository.existsById(request.getId())).isFalse();
		assertThat(positionRepository.existsById(position.getId())).isFalse();
		assertThat(positionSkillRepository.existsById(link.getId())).isFalse();
		assertThat(teamRepository.existsById(request.getTeam().getId())).isTrue();
		assertThat(skillRepository.existsById(skill.getId())).isTrue();
	}

	@Test
	void deletingPositionOnlyRemovesItsSkillLinks() {
		TeamRequest request = createRequest(createTeam());
		TeamRequestPosition first = createPosition(request, "Backend");
		TeamRequestPosition second = createPosition(request, "Fullstack");
		Skill skill = createSkill("Java");
		TeamRequestPositionSkill removed = addSkill(first, skill);
		TeamRequestPositionSkill retained = addSkill(second, skill);

		positionRepository.deleteById(first.getId());

		assertThat(positionSkillRepository.existsById(removed.getId())).isFalse();
		assertThat(positionSkillRepository.existsById(retained.getId())).isTrue();
		assertThat(requestRepository.existsById(request.getId())).isTrue();
		assertThat(skillRepository.existsById(skill.getId())).isTrue();
	}

	@Test
	void skillReferencedByPositionCannotBeDeleted() {
		TeamRequestPosition position = createPosition(createRequest(createTeam()), "Backend");
		Skill skill = createSkill("Java");
		addSkill(position, skill);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"DELETE FROM skills WHERE id = ?", skill.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private Team createTeam() {
		Profile owner = new Profile();
		owner.setDisplayName("Owner");
		Team team = new Team();
		team.setOwnerProfile(profileRepository.saveAndFlush(owner));
		team.setName("Hackathon team");
		return teamRepository.saveAndFlush(team);
	}

	private TeamRequest createRequest(Team team) {
		TeamRequest request = new TeamRequest();
		request.setTeam(team);
		request.setTitle("Hackathon recruitment");
		request.setDescription("A service for students");
		request.setRecruitmentDeadline(LocalDateTime.now().plusDays(7).withNano(0));
		request.setTargetTeamSize(4);
		return requestRepository.saveAndFlush(request);
	}

	private TeamRequestPosition createPosition(TeamRequest request, String name) {
		TeamRequestPosition position = new TeamRequestPosition();
		position.setRequest(request);
		position.setName(name);
		position.setDescription("Build the product");
		position.setSlotsCount(1);
		return positionRepository.saveAndFlush(position);
	}

	private Skill createSkill(String name) {
		Skill skill = new Skill();
		skill.setName(name + " " + UUID.randomUUID());
		return skillRepository.saveAndFlush(skill);
	}

	private TeamRequestPositionSkill addSkill(TeamRequestPosition position, Skill skill) {
		TeamRequestPositionSkill link = new TeamRequestPositionSkill();
		link.setId(new TeamRequestPositionSkillId(position.getId(), skill.getId()));
		link.setPosition(position);
		link.setSkill(skill);
		return positionSkillRepository.saveAndFlush(link);
	}
}
