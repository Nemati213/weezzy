package ru.itmo.nemat.weezzy.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

public final class TeamRecommendationTestData {
	private TeamRecommendationTestData() {
	}

	public static void upsertImpression(
			JdbcTemplate jdbc, UUID ownerUserId, UUID teamId, LocalDateTime shownAt
	) {
		jdbc.update("""
				INSERT INTO team_recommendation_impressions (owner_user_id, team_id, last_shown_at)
				VALUES (?, ?, ?)
				ON CONFLICT (owner_user_id, team_id) DO UPDATE
				SET last_shown_at = GREATEST(team_recommendation_impressions.last_shown_at,
				                            EXCLUDED.last_shown_at)
				""", ownerUserId, teamId, shownAt);
	}
}
