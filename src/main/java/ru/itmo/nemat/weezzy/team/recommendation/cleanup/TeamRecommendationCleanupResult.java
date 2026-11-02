package ru.itmo.nemat.weezzy.team.recommendation.cleanup;

public record TeamRecommendationCleanupResult(long sessions, long entries, long impressions) {
	public boolean isEmpty() {
		return sessions == 0 && entries == 0 && impressions == 0;
	}

	public TeamRecommendationCleanupResult plus(TeamRecommendationCleanupResult other) {
		return new TeamRecommendationCleanupResult(sessions + other.sessions,
				entries + other.entries, impressions + other.impressions);
	}
}
