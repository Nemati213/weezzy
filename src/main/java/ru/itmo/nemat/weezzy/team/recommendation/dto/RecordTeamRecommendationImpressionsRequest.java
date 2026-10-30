package ru.itmo.nemat.weezzy.team.recommendation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record RecordTeamRecommendationImpressionsRequest(
		@NotNull @Size(min = 1, max = 50) List<@NotNull UUID> entryIds
) {
}
