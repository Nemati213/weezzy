package ru.itmo.nemat.weezzy.team.recommendation.ranking;

import java.util.List;
import java.util.UUID;

public record RankedTeamPosition(
		TeamPositionCandidate position,
		List<UUID> matchedSkillIds,
		List<UUID> unmatchedSkillIds
) {
	public RankedTeamPosition {
		matchedSkillIds = List.copyOf(matchedSkillIds);
		unmatchedSkillIds = List.copyOf(unmatchedSkillIds);
	}

	public int matchedCount() {
		return matchedSkillIds.size();
	}

	public int requiredCount() {
		return position.skillIds().size();
	}
}
