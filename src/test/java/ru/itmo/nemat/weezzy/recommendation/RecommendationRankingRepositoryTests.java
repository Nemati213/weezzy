package ru.itmo.nemat.weezzy.recommendation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.recommendation.dto.RecommendationFilter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
		"app.recommendation.weights.skill=11",
		"app.recommendation.weights.interest=7",
		"app.recommendation.weights.goal=13"
})
class RecommendationRankingRepositoryTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private RecommendationRankingRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void rankingUsesConfiguredWeightsGoalFilterAndSqlCursor() {
		UUID sourceId = insertProfile("SQL Ranking Source", "FICT", 2, "DRAFT");
		UUID combinedId = insertProfile("SQL Ranking Combined", "FICT", 2, "ACTIVE");
		UUID goalId = insertProfile("SQL Ranking Goal", "FICT", 3, "ACTIVE");
		UUID skillId = insertProfile("SQL Ranking Skill", "CT", 4, "ACTIVE");
		UUID skill = insertSkill("SQL Ranking Skill Signal");
		UUID interest = insertInterest("SQL Ranking Interest Signal");
		UUID goal = insertGoal("SQL_RANKING_GOAL", "SQL Ranking Goal Signal");

		link("profile_skills", "skill_id", sourceId, skill);
		link("profile_interests", "interest_id", sourceId, interest);
		link("profile_goals", "goal_id", sourceId, goal);
		link("profile_skills", "skill_id", combinedId, skill);
		link("profile_interests", "interest_id", combinedId, interest);
		link("profile_goals", "goal_id", goalId, goal);
		link("profile_skills", "skill_id", skillId, skill);

		RecommendationFilter emptyFilter = new RecommendationFilter(
				null,
				null,
				null,
				null,
				null,
				null
		);
		List<RankedProfileProjection> firstPage = repository.findRankedProfiles(
				sourceId,
				null,
				2,
				emptyFilter,
				LocalDateTime.now().minusDays(7)
		);

		assertThat(firstPage)
				.extracting(RankedProfileProjection::profileId)
				.containsExactly(combinedId, goalId);
		assertThat(firstPage)
				.extracting(RankedProfileProjection::score)
				.containsExactly(18, 13);
		assertThat(firstPage.getFirst().matchedSkillCount()).isEqualTo(1);
		assertThat(firstPage.getFirst().matchedInterestCount()).isEqualTo(1);

		RankedProfileProjection last = firstPage.getLast();
		List<RankedProfileProjection> secondPage = repository.findRankedProfiles(
				sourceId,
				new RecommendationCursor(last.score(), last.profileId()),
				2,
				emptyFilter,
				LocalDateTime.now().minusDays(7)
		);
		assertThat(secondPage)
				.extracting(RankedProfileProjection::profileId)
				.containsExactly(skillId);

		RecommendationFilter goalFilter = new RecommendationFilter(
				null,
				null,
				null,
				null,
				null,
				Set.of(goal)
		);
		assertThat(repository.findRankedProfiles(
				sourceId,
				null,
				10,
				goalFilter,
				LocalDateTime.now().minusDays(7)
		)).extracting(RankedProfileProjection::profileId)
				.containsExactly(goalId);
	}

	@Test
	@Timeout(30)
	void rankingHandlesThreeThousandProfilesWithoutLoadingThemIntoJava() {
		UUID sourceId = insertProfile("SQL Load Source", "FICT", 2, "DRAFT");
		UUID skillId = insertSkill("SQL Load Shared Skill");
		link("profile_skills", "skill_id", sourceId, skillId);
		String prefix = "load-" + UUID.randomUUID() + "-";

		jdbcTemplate.update("""
				INSERT INTO profiles (
				    id, display_name, bio, telegram, faculty,
				    study_program, course, created_at, updated_at, status
				)
				SELECT (md5(? || number::text))::uuid,
				       'Load Candidate ' || number,
				       'Load test profile',
				       '@load_test',
				       'FICT',
				       'Software Engineering',
				       2,
				       CURRENT_TIMESTAMP,
				       CURRENT_TIMESTAMP,
				       'ACTIVE'
				FROM generate_series(1, 3000) AS number
				""", prefix);
		jdbcTemplate.update("""
				INSERT INTO profile_skills (profile_id, skill_id, created_at)
				SELECT (md5(? || number::text))::uuid,
				       ?::uuid,
				       CURRENT_TIMESTAMP
				FROM generate_series(1, 3000) AS number
				""", prefix, skillId.toString());

		long startedAt = System.nanoTime();
		List<RankedProfileProjection> result = repository.findRankedProfiles(
				sourceId,
				null,
				51,
				new RecommendationFilter(null, null, null, null, null, null),
				LocalDateTime.now().minusDays(7)
		);
		Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

		assertThat(result).hasSize(51);
		assertThat(result).allSatisfy(ranked -> {
			assertThat(ranked.score()).isEqualTo(11);
			assertThat(ranked.matchedSkillCount()).isEqualTo(1);
		});
		assertThat(elapsed).isLessThan(Duration.ofSeconds(10));
	}

	private UUID insertProfile(
			String displayName,
			String faculty,
			int course,
			String status
	) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO profiles (
				    id, display_name, bio, telegram, faculty,
				    study_program, course, created_at, updated_at, status
				) VALUES (?, ?, 'Ranking test', '@ranking_test', ?,
				          'Software Engineering', ?, CURRENT_TIMESTAMP,
				          CURRENT_TIMESTAMP, ?)
				""", id, displayName, faculty, course, status);
		return id;
	}

	private UUID insertSkill(String name) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO skills (id, name, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
				id,
				name
		);
		return id;
	}

	private UUID insertInterest(String name) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update(
				"INSERT INTO interests (id, name, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
				id,
				name
		);
		return id;
	}

	private UUID insertGoal(String code, String name) {
		UUID id = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO goals (id, code, name, created_at)
				VALUES (?, ?, ?, CURRENT_TIMESTAMP)
				""", id, code, name);
		return id;
	}

	private void link(
			String table,
			String signalColumn,
			UUID profileId,
			UUID signalId
	) {
		String sql = "INSERT INTO " + table
				+ " (profile_id, " + signalColumn + ", created_at) "
				+ "VALUES (?, ?, CURRENT_TIMESTAMP)";
		jdbcTemplate.update(sql, profileId, signalId);
	}
}
