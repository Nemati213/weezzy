package ru.itmo.nemat.weezzy.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

public final class TeamPositionTestData {
	private TeamPositionTestData() {
	}

	public static UUID positionId(JdbcTemplate jdbc, UUID teamId) {
		var ids = jdbc.queryForList(
				"SELECT id FROM team_positions WHERE team_id = ? AND name = 'Test position'",
				UUID.class, teamId);
		if (!ids.isEmpty()) {
			return ids.getFirst();
		}
		if (Boolean.FALSE.equals(jdbc.queryForObject(
				"SELECT EXISTS(SELECT 1 FROM teams WHERE id = ?)", Boolean.class, teamId))) {
			return UUID.randomUUID();
		}
		return createPosition(jdbc, teamId, "Test position", 100);
	}

	public static UUID createPosition(JdbcTemplate jdbc, UUID teamId, String name, int capacity) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO team_positions (id, team_id, name, capacity) VALUES (?, ?, ?, ?)
				""", id, teamId, name, capacity);
		return id;
	}
}
