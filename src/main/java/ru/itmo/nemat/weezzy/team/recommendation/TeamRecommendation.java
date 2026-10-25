package ru.itmo.nemat.weezzy.team.recommendation;

import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationPositionResponse;
import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationTeamResponse;

import java.util.List;
import java.util.UUID;

public record TeamRecommendation(
		TeamRecommendationTeamResponse team,
		UUID recommendedTeamPositionId,
		List<TeamRecommendationPositionResponse> options
) {
	public TeamRecommendation {
		options = List.copyOf(options);
	}
}
