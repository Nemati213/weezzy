package ru.itmo.nemat.weezzy.team.recommendation.dto;

import ru.itmo.nemat.weezzy.team.request.position.dto.TeamRequestPositionResponse;

import java.util.List;
import java.util.UUID;

public record TeamRecommendationPositionResponse(
		TeamRecommendationRequestResponse request,
		TeamRequestPositionResponse position,
		List<UUID> matchedSkillIds,
		List<UUID> unmatchedSkillIds
) {
	public TeamRecommendationPositionResponse {
		matchedSkillIds = List.copyOf(matchedSkillIds);
		unmatchedSkillIds = List.copyOf(unmatchedSkillIds);
	}
}
