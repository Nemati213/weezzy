package ru.itmo.nemat.weezzy.recommendation.dto;

public record ProfileRecommendationReasonResponse(
		ProfileRecommendationScoreBreakdownResponse scoreBreakdown,
		ProfileRecommendationSignalCountsResponse matchedCounts
) {

}
