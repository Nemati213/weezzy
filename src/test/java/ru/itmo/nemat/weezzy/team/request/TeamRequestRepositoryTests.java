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
import ru.itmo.nemat.weezzy.team.position.TeamPosition;
import ru.itmo.nemat.weezzy.team.position.TeamPositionRepository;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPositionRepository;
import ru.itmo.nemat.weezzy.team.position.skill.TeamPositionSkill;
import ru.itmo.nemat.weezzy.team.position.skill.TeamPositionSkillId;
import ru.itmo.nemat.weezzy.team.position.skill.TeamPositionSkillRepository;

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
	private TeamPositionRepository positionRepository;
	@Autowired
	private TeamRequestPositionRepository linkRepository;

	@Autowired
	private TeamPositionSkillRepository positionSkillRepository;

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
		TeamPosition backend = createPosition(request, "Backend");
		TeamPosition designer = createPosition(request, "Designer");
		Skill java = createSkill("Java");
		Skill figma = createSkill("Figma");
		addSkill(backend, java);
		addSkill(designer, figma);

		TeamRequest found = requestRepository.findById(request.getId()).orElseThrow();
		assertThat(found.getTeam().getId()).isEqualTo(team.getId());
		assertThat(found.getTitle()).isEqualTo("Hackathon recruitment");
		assertThat(found.getDescription()).isEqualTo("A service for students");
		assertThat(found.getStatus()).isEqualTo(TeamRequestStatus.OPEN);
		assertThat(found.getRecruitmentDeadline())
				.isEqualTo(request.getRecruitmentDeadline());
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
		assertThat(linkRepository.countByRequestId(request.getId())).isEqualTo(2);
		assertThat(linkRepository.findByRequestIdOrderByCreatedAtAscIdAsc(request.getId()))
				.extracting(link -> link.getPosition().getId())
				.containsExactlyInAnyOrder(backend.getId(), designer.getId());
		TeamPosition foundPosition = positionRepository.findById(backend.getId())
				.orElseThrow();
		assertThat(foundPosition.getTeam().getId()).isEqualTo(team.getId());
		assertThat(foundPosition.getName()).isEqualTo("Backend");
		assertThat(foundPosition.getDescription()).isEqualTo("Build the product");
		assertThat(foundPosition.getCapacity()).isEqualTo(1);
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
		TeamPosition created = createPosition(createRequest(createTeam()), "Backend");
		TeamPosition position = positionRepository.findById(created.getId())
				.orElseThrow();
		position.setCapacity(2);
		positionRepository.saveAndFlush(position);

		TeamPosition found = positionRepository.findById(position.getId()).orElseThrow();
		assertThat(found.getCapacity()).isEqualTo(2);
		assertThat(found.getCreatedAt()).isEqualTo(position.getCreatedAt());
		assertThat(found.getUpdatedAt()).isAfter(position.getUpdatedAt());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   "})
	void blankRequestTitleAndPositionNameAreRejectedByDatabase(String text) {
		TeamRequest request = createRequest(createTeam());
		TeamPosition position = createPosition(request, "Backend");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE team_requests SET title = ? WHERE id = ?", text, request.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE team_positions SET name = ? WHERE id = ?", text, position.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1})
	void nonPositivePositionCapacityIsRejectedByDatabase(int count) {
		TeamRequest request = createRequest(createTeam());
		TeamPosition position = createPosition(request, "Backend");

		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE team_positions SET capacity = ? WHERE id = ?",
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
		TeamPosition position = createPosition(request, "Backend");
		Skill skill = createSkill("Java");
		addSkill(position, skill);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE team_requests SET team_id = ? WHERE id = ?",
				UUID.randomUUID(), request.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE team_positions SET team_id = ? WHERE id = ?",
				UUID.randomUUID(), position.getId()
		)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO team_position_skills (position_id, skill_id)
				VALUES (?, ?)
				""", UUID.randomUUID(), skill.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO team_position_skills (position_id, skill_id)
				VALUES (?, ?)
				""", position.getId(), UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sameSkillCannotBeLinkedTwiceToPositionButCanBeUsedInAnotherPosition() {
		TeamRequest request = createRequest(createTeam());
		TeamPosition first = createPosition(request, "Backend");
		TeamPosition second = createPosition(request, "Fullstack");
		Skill skill = createSkill("Java");
		addSkill(first, skill);
		addSkill(second, skill);

		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO team_position_skills (position_id, skill_id)
				VALUES (?, ?)
				""", first.getId(), skill.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(positionSkillRepository.findByPositionIdOrderBySkillIdAsc(second.getId()))
				.hasSize(1);
	}

	@Test
	void deletingRequestPreservesTeamPositionsAndSkills() {
		TeamRequest request = createRequest(createTeam());
		TeamPosition position = createPosition(request, "Backend");
		Skill skill = createSkill("Java");
		TeamPositionSkill link = addSkill(position, skill);

		requestRepository.deleteById(request.getId());

		assertThat(requestRepository.existsById(request.getId())).isFalse();
		assertThat(positionRepository.existsById(position.getId())).isTrue();
		assertThat(positionSkillRepository.existsById(link.getId())).isTrue();
		assertThat(teamRepository.existsById(request.getTeam().getId())).isTrue();
		assertThat(skillRepository.existsById(skill.getId())).isTrue();
	}

	@Test
	void publishedPositionCannotBeDeleted() {
		TeamRequest request = createRequest(createTeam());
		TeamPosition first = createPosition(request, "Backend");
		TeamPosition second = createPosition(request, "Fullstack");
		Skill skill = createSkill("Java");
		TeamPositionSkill removed = addSkill(first, skill);
		TeamPositionSkill retained = addSkill(second, skill);

		assertThatThrownBy(() -> positionRepository.deleteById(first.getId()))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(positionSkillRepository.existsById(removed.getId())).isTrue();
		assertThat(positionSkillRepository.existsById(retained.getId())).isTrue();
		assertThat(requestRepository.existsById(request.getId())).isTrue();
		assertThat(skillRepository.existsById(skill.getId())).isTrue();
	}

	@Test
	void skillReferencedByPositionCannotBeDeleted() {
		TeamPosition position = createPosition(createRequest(createTeam()), "Backend");
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
		team.setPurpose("Build project");
		return teamRepository.saveAndFlush(team);
	}

	private TeamRequest createRequest(Team team) {
		TeamRequest request = new TeamRequest();
		request.setTeam(team);
		request.setTitle("Hackathon recruitment");
		request.setDescription("A service for students");
		request.setRecruitmentDeadline(LocalDateTime.now().plusDays(7).withNano(0));
		return requestRepository.saveAndFlush(request);
	}

	private TeamPosition createPosition(TeamRequest request, String name) {
		TeamPosition position = new TeamPosition();
		position.setTeam(request.getTeam());
		position.setName(name);
		position.setDescription("Build the product");
		position.setCapacity(1);
		position = positionRepository.saveAndFlush(position);
		TeamRequestPosition link = new TeamRequestPosition();
		link.setRequest(request);
		link.setPosition(position);
		linkRepository.saveAndFlush(link);
		return position;
	}

	private Skill createSkill(String name) {
		Skill skill = new Skill();
		skill.setName(name + " " + UUID.randomUUID());
		return skillRepository.saveAndFlush(skill);
	}

	private TeamPositionSkill addSkill(TeamPosition position, Skill skill) {
		TeamPositionSkill link = new TeamPositionSkill();
		link.setId(new TeamPositionSkillId(position.getId(), skill.getId()));
		link.setPosition(position);
		link.setSkill(skill);
		return positionSkillRepository.saveAndFlush(link);
	}
}
