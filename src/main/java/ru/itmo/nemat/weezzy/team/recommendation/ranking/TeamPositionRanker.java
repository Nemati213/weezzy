package ru.itmo.nemat.weezzy.team.recommendation.ranking;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class TeamPositionRanker {
	private static final Comparator<UUID> UUID_ORDER = (left, right) -> {
		int mostSignificant = Long.compareUnsigned(
				left.getMostSignificantBits(), right.getMostSignificantBits());
		return mostSignificant != 0 ? mostSignificant : Long.compareUnsigned(
				left.getLeastSignificantBits(), right.getLeastSignificantBits());
	};

	private static final Comparator<RankedTeamPosition> POSITION_ORDER = Comparator
			.comparingInt(RankedTeamPosition::matchedCount).reversed()
			.thenComparing(TeamPositionRanker::compareCoverageDescending)
			.thenComparing(ranked -> ranked.position().createdAt())
			.thenComparing(ranked -> ranked.position().positionId(), UUID_ORDER);

	public List<RankedTeamPosition> rank(
			Set<UUID> profileSkillIds, Collection<TeamPositionCandidate> positions
	) {
		return positions.stream()
				.map(position -> evaluate(profileSkillIds, position))
				.sorted(POSITION_ORDER)
				.toList();
	}

	private RankedTeamPosition evaluate(
			Set<UUID> profileSkillIds, TeamPositionCandidate position
	) {
		List<UUID> matched = new ArrayList<>();
		List<UUID> unmatched = new ArrayList<>();
		for (UUID skillId : position.skillIds()) {
			if (profileSkillIds.contains(skillId)) {
				matched.add(skillId);
			} else {
				unmatched.add(skillId);
			}
		}
		matched.sort(UUID_ORDER);
		unmatched.sort(UUID_ORDER);
		return new RankedTeamPosition(position, matched, unmatched);
	}

	private static int compareCoverageDescending(
			RankedTeamPosition left, RankedTeamPosition right
	) {
		long leftProduct = (long) left.matchedCount() * Math.max(1, right.requiredCount());
		long rightProduct = (long) right.matchedCount() * Math.max(1, left.requiredCount());
		return Long.compare(rightProduct, leftProduct);
	}
}
