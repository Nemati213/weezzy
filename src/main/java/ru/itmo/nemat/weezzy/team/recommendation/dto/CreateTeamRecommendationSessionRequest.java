package ru.itmo.nemat.weezzy.team.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTeamRecommendationSessionRequest(
		@NotNull UUID clientRequestId,
		@Min(1) @Max(50) Integer pageSize
) {
	public CreateTeamRecommendationSessionRequest {
		if (pageSize == null) {
			pageSize = 20;
		}
	}
}
