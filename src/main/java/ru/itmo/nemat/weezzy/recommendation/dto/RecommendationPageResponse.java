package ru.itmo.nemat.weezzy.recommendation.dto;

import java.util.List;

public record RecommendationPageResponse(
		List<ProfileRecommendationResponse> content,
		String nextCursor
) {

}
