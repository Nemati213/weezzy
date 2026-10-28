package ru.itmo.nemat.weezzy.team.recommendation.session;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TeamRecommendationSessionWriteRepository {
	private static final int BATCH_SIZE = 500;
	private final JdbcTemplate jdbc;

	public boolean serializeCreation(UUID ownerUserId) {
		// A new tuple version makes waiting REPEATABLE_READ creators retry their snapshot.
		return jdbc.update("UPDATE users SET id = id WHERE id = ?", ownerUserId) == 1;
	}

	public void insertEntries(UUID sessionId, List<UUID> teamIds) {
		for (int start = 0; start < teamIds.size(); start += BATCH_SIZE) {
			int offset = start;
			List<UUID> batch = teamIds.subList(start, Math.min(start + BATCH_SIZE, teamIds.size()));
			jdbc.batchUpdate("""
					INSERT INTO team_recommendation_entries (id, session_id, ordinal, team_id)
					VALUES (?, ?, ?, ?)
					""", new BatchPreparedStatementSetter() {
				@Override
				public void setValues(PreparedStatement statement, int index) throws SQLException {
					statement.setObject(1, UUID.randomUUID());
					statement.setObject(2, sessionId);
					statement.setInt(3, offset + index);
					statement.setObject(4, batch.get(index));
				}

				@Override
				public int getBatchSize() {
					return batch.size();
				}
			});
		}
	}
}
