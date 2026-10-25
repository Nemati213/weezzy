package ru.itmo.nemat.weezzy.team.recommendation.candidate;

import java.time.LocalDateTime;
import java.util.UUID;

public record TeamRecommendationCandidate(
		UUID teamId,
		UUID requestId,
		LocalDateTime requestCreatedAt,
		LocalDateTime recruitmentDeadline,
		UUID requestPositionId,
		UUID teamPositionId,
		LocalDateTime positionCreatedAt
) {
}
