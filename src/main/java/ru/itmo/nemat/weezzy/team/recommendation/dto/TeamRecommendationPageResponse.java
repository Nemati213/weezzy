package ru.itmo.nemat.weezzy.team.recommendation.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TeamRecommendationPageResponse(
		UUID sessionId, LocalDateTime createdAt, LocalDateTime expiresAt, int pageSize,
		List<TeamRecommendationResponse> items,
		String pageCursor, String nextCursor, String previousCursor
) {
	public TeamRecommendationPageResponse {
		items = List.copyOf(items);
	}
}
