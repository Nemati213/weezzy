package ru.itmo.nemat.weezzy.recommendation.dto;

public record ProfileRecommendationSignalCountsResponse(
		int skills,
		int interests,
		int goals
) {

}
