package ru.itmo.nemat.weezzy.recommendation.dto;

public record ProfileRecommendationScoreBreakdownResponse(
		int skills,
		int interests,
		int goals
) {

}
