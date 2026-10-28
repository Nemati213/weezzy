package ru.itmo.nemat.weezzy.team.recommendation.session;

import java.time.LocalDateTime;
import java.util.UUID;

public record TeamRecommendationSessionResult(
		UUID sessionId, LocalDateTime createdAt, LocalDateTime expiresAt,
		int pageSize, int entryCount, boolean created
) {
	static TeamRecommendationSessionResult from(TeamRecommendationSession session, boolean created) {
		return new TeamRecommendationSessionResult(session.getId(), session.getCreatedAt(),
				session.getExpiresAt(), session.getPageSize(), session.getEntryCount(), created);
	}
}
