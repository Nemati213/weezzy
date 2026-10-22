package ru.itmo.nemat.weezzy.team.recommendation.ranking;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record TeamPositionCandidate(
		UUID positionId,
		LocalDateTime createdAt,
		Set<UUID> skillIds
) {
	public TeamPositionCandidate {
		Objects.requireNonNull(positionId);
		Objects.requireNonNull(createdAt);
		skillIds = Set.copyOf(skillIds);
	}
}
