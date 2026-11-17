package ru.itmo.nemat.weezzy.team.recommendation.cleanup;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TeamRecommendationCleanupRepository {
	private final NamedParameterJdbcTemplate jdbc;

	public List<UUID> lockExpiredSessions(LocalDateTime now, int batchSize) {
		return jdbc.queryForList("""
				SELECT id FROM team_recommendation_sessions
				WHERE expires_at <= :now ORDER BY expires_at, id
				LIMIT :batchSize FOR UPDATE SKIP LOCKED
				""", Map.of("now", now, "batchSize", batchSize), UUID.class);
	}

	public int deleteEntries(List<UUID> sessionIds, int batchSize) {
		return jdbc.update("""
				DELETE FROM team_recommendation_entries
				WHERE ctid = ANY (ARRAY(
				    SELECT candidate.ctid
				    FROM unnest(ARRAY[:sessionIds]::uuid[]) AS session(id)
				    CROSS JOIN LATERAL (
				        SELECT ctid FROM team_recommendation_entries
				        WHERE session_id = session.id ORDER BY ordinal
				        LIMIT :batchSize FOR UPDATE SKIP LOCKED
				    ) candidate
				    LIMIT :batchSize
				))
				""", Map.of("sessionIds", sessionIds, "batchSize", batchSize));
	}

	public int deleteEmptySessions(List<UUID> sessionIds) {
		return jdbc.update("""
				DELETE FROM team_recommendation_sessions session
				WHERE session.id IN (:sessionIds) AND NOT EXISTS (
				    SELECT 1 FROM team_recommendation_entries entry
				    WHERE entry.session_id = session.id
				)
				""", Map.of("sessionIds", sessionIds));
	}

	public int deleteOldImpressions(LocalDateTime cutoff, int batchSize) {
		return jdbc.update("""
				DELETE FROM team_recommendation_impressions impression
				USING (
				    SELECT owner_user_id, team_id FROM team_recommendation_impressions
				    WHERE last_shown_at < :cutoff
				    ORDER BY last_shown_at, owner_user_id, team_id
				    LIMIT :batchSize FOR UPDATE SKIP LOCKED
				) expired
				WHERE impression.owner_user_id = expired.owner_user_id
				  AND impression.team_id = expired.team_id
				  AND impression.last_shown_at < :cutoff
				""", Map.of("cutoff", cutoff, "batchSize", batchSize));
	}
}
