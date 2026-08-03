package ru.itmo.nemat.weezzy.recommendation;

import java.util.UUID;

public record RankedProfileProjection(
		UUID profileId,
		int score,
		int matchedSkillCount,
		int matchedInterestCount,
		int matchedGoalCount
) {
}
