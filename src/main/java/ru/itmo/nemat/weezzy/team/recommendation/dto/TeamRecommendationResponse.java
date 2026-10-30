package ru.itmo.nemat.weezzy.team.recommendation.dto;

import ru.itmo.nemat.weezzy.team.recommendation.TeamRecommendation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TeamRecommendationResponse(
		UUID entryId,
		TeamRecommendationTeamResponse team,
		UUID recommendedTeamPositionId,
		List<TeamRecommendationPositionResponse> options
) {
	public TeamRecommendationResponse {
		Objects.requireNonNull(entryId);
		options = List.copyOf(options);
	}

	public static TeamRecommendationResponse from(UUID entryId, TeamRecommendation recommendation) {
		return new TeamRecommendationResponse(entryId, recommendation.team(),
				recommendation.recommendedTeamPositionId(), recommendation.options());
	}
}
