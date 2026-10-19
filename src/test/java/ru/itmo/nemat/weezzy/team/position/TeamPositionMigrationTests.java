package ru.itmo.nemat.weezzy.team.position;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class TeamPositionMigrationTests {
	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres"));

	@Test
	void migrationPreservesMembershipApplicationsInvitationsAndPositionIdentity() {
		var source = new DriverManagerDataSource(postgres.getJdbcUrl(),
				postgres.getUsername(), postgres.getPassword());
		Flyway.configure().dataSource(source).target("48").load().migrate();
		JdbcTemplate jdbc = new JdbcTemplate(source);
		UUID owner = profile(jdbc);
		UUID member = profile(jdbc);
		UUID candidate = profile(jdbc);
		UUID team = UUID.randomUUID();
		UUID request = UUID.randomUUID();
		UUID position = UUID.randomUUID();
		UUID application = UUID.randomUUID();
		UUID invitation = UUID.randomUUID();
		jdbc.update("INSERT INTO teams (id, owner_profile_id, name) VALUES (?, ?, 'Project')", team, owner);
		jdbc.update("""
				INSERT INTO team_requests (id, team_id, title, recruitment_deadline, target_team_size)
				VALUES (?, ?, 'Recruitment', NOW() + INTERVAL '1 day', 3)
				""", request, team);
		jdbc.update("""
				INSERT INTO team_request_positions (id, request_id, name, slots_count)
				VALUES (?, ?, 'Backend', 2)
				""", position, request);
		jdbc.update("INSERT INTO team_members (team_id, profile_id) VALUES (?, ?)", team, owner);
		jdbc.update("INSERT INTO team_members (team_id, profile_id, position_id) VALUES (?, ?, ?)",
				team, member, position);
		jdbc.update("""
				INSERT INTO team_applications (id, position_id, applicant_profile_id, status, resolved_at)
				VALUES (?, ?, ?, 'ACCEPTED', NOW())
				""", application, position, member);
		jdbc.update("""
				INSERT INTO team_invitations (id, team_id, inviter_profile_id, invitee_profile_id)
				VALUES (?, ?, ?, ?)
				""", invitation, team, owner, candidate);
		Flyway.configure().dataSource(source).load().migrate();
		assertThat(jdbc.queryForObject("SELECT purpose FROM teams WHERE id = ?", String.class, team))
				.isEqualTo("Project");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team_members WHERE position_id IS NULL",
				Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT position_id FROM team_members WHERE profile_id = ?",
				UUID.class, member)).isEqualTo(position);
		assertThat(jdbc.queryForObject("SELECT team_position_id FROM team_request_positions WHERE id = ?",
				UUID.class, position)).isEqualTo(position);
		assertThat(jdbc.queryForObject("SELECT status FROM team_applications WHERE id = ?",
				String.class, application)).isEqualTo("ACCEPTED");
		assertThat(jdbc.queryForObject("SELECT position_id FROM team_invitations WHERE id = ?",
				UUID.class, invitation)).isNotNull();
		assertThatThrownBy(() -> jdbc.update("UPDATE team_members SET position_id = NULL WHERE profile_id = ?",
				owner)).isInstanceOf(DataIntegrityViolationException.class);
		UUID otherTeam = UUID.randomUUID();
		jdbc.update("INSERT INTO teams (id, owner_profile_id, name, purpose) VALUES (?, ?, 'Other', 'Other goal')",
				otherTeam, candidate);
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO team_members (team_id, profile_id, position_id) VALUES (?, ?, ?)
				""", otherTeam, candidate, position)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private UUID profile(JdbcTemplate jdbc) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO profiles (id, display_name, status, created_at, updated_at)
				VALUES (?, 'User', 'DRAFT', NOW(), NOW())
				""", id);
		return id;
	}
}
