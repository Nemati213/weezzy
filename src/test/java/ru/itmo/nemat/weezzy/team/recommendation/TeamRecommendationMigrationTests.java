package ru.itmo.nemat.weezzy.team.recommendation;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class TeamRecommendationMigrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));

	@Test
	void upgradesV50WithoutChangingRecruitmentDataOrDeclineHistory() {
		var source = new DriverManagerDataSource(postgres.getJdbcUrl(),
				postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(source).target("50").load().migrate();
		var jdbc = new JdbcTemplate(source);
		UUID profile = UUID.randomUUID();
		UUID team = UUID.randomUUID();
		UUID position = UUID.randomUUID();
		UUID request = UUID.randomUUID();
		UUID link = UUID.randomUUID();
		UUID application = UUID.randomUUID();
		LocalDateTime resolved = LocalDateTime.of(2026, 10, 28, 12, 0);
		jdbc.update("""
				INSERT INTO profiles (id, display_name, status, created_at, updated_at)
				VALUES (?, 'Owner', 'ACTIVE', NOW(), NOW())
				""", profile);
		jdbc.update("INSERT INTO teams (id, owner_profile_id, name, purpose) VALUES (?, ?, 'Team', 'Project')",
				team, profile);
		jdbc.update("INSERT INTO team_positions (id, team_id, name, capacity) VALUES (?, ?, 'Backend', 2)",
				position, team);
		jdbc.update("""
				INSERT INTO team_requests (id, team_id, title, recruitment_deadline)
				VALUES (?, ?, 'Recruitment', NOW() + INTERVAL '1 day')
				""", request, team);
		jdbc.update("INSERT INTO team_request_positions (id, request_id, team_position_id) VALUES (?, ?, ?)",
				link, request, position);
		jdbc.update("""
				INSERT INTO team_applications
				(id, position_id, applicant_profile_id, status, resolved_at)
				VALUES (?, ?, ?, 'DECLINED', ?)
				""", application, link, profile, resolved);

		var flyway = Flyway.configure().dataSource(source).target("51").load();
		assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
		flyway.validate();
		assertThat(jdbc.queryForObject("""
				SELECT a.resolved_at FROM team_applications a
				JOIN team_request_positions link ON link.id = a.position_id
				JOIN team_requests r ON r.id = link.request_id
				JOIN team_positions p ON p.id = link.team_position_id
				WHERE a.id = ? AND a.status = 'DECLINED' AND p.id = ? AND r.team_id = ?
				""", LocalDateTime.class, application, position, team)).isEqualTo(resolved);
		assertThat(jdbc.queryForObject("""
				SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_team_applications_declined_lookup'
				""", String.class)).contains("applicant_profile_id, position_id, resolved_at DESC", "DECLINED");
		assertThat(jdbc.queryForList("""
				SELECT indexname FROM pg_indexes
				WHERE tablename IN ('team_recommendation_sessions', 'team_recommendation_entries',
				                    'team_recommendation_impressions')
				""", String.class)).contains(
				"uq_team_recommendation_session_request", "uq_team_recommendation_entry_ordinal",
				"uq_team_recommendation_entry_team", "idx_team_recommendation_sessions_owner_created",
				"idx_team_recommendation_sessions_expiry", "idx_team_recommendation_entries_team",
				"idx_team_recommendation_impressions_owner_shown",
				"idx_team_recommendation_impressions_retention", "idx_team_recommendation_impressions_team");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team_recommendation_sessions", Long.class))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team_recommendation_entries", Long.class))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team_recommendation_impressions", Long.class))
				.isZero();
		assertThat(flyway.migrate().migrationsExecuted).isZero();
	}
}
