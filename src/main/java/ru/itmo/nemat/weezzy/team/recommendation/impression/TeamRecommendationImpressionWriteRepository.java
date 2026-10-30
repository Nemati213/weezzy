package ru.itmo.nemat.weezzy.team.recommendation.impression;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TeamRecommendationImpressionWriteRepository {
	private final NamedParameterJdbcTemplate jdbc;

	public List<UUID> lockTeams(Collection<UUID> teamIds) {
		return jdbc.queryForList("""
				SELECT id FROM teams WHERE id IN (:teamIds)
				ORDER BY id FOR KEY SHARE
				""", Map.of("teamIds", teamIds), UUID.class);
	}

	public void record(UUID ownerUserId, UUID sessionId, Collection<UUID> entryIds,
			Collection<UUID> teamIds, LocalDateTime now) {
		// Refresh the tuple so waiting REPEATABLE_READ creators retry their snapshot.
		jdbc.update("UPDATE users SET id = id WHERE id = :ownerUserId",
				Map.of("ownerUserId", ownerUserId));
		jdbc.update("""
				WITH shown AS (
				    UPDATE team_recommendation_entries
				    SET first_shown_at = GREATEST(:now, first_delivered_at)
				    WHERE session_id = :sessionId AND id IN (:entryIds)
				      AND team_id IN (:teamIds) AND first_delivered_at IS NOT NULL
				      AND first_shown_at IS NULL
				    RETURNING team_id, first_shown_at
				)
				INSERT INTO team_recommendation_impressions
				    (owner_user_id, team_id, last_shown_at)
				SELECT :ownerUserId, team_id, first_shown_at FROM shown ORDER BY team_id
				ON CONFLICT (owner_user_id, team_id) DO UPDATE
				SET last_shown_at = GREATEST(team_recommendation_impressions.last_shown_at,
				                            EXCLUDED.last_shown_at)
				""", Map.of("ownerUserId", ownerUserId, "sessionId", sessionId,
				"entryIds", entryIds, "teamIds", teamIds, "now", now));
	}
}
