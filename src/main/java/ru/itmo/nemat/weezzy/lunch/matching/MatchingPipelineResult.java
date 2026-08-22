package ru.itmo.nemat.weezzy.lunch.matching;

import java.util.List;
import java.util.Objects;

public record MatchingPipelineResult(
		List<MatchingGroup> groups,
		List<MatchingCandidate> remainingCandidates
) {
	public MatchingPipelineResult {
		groups = List.copyOf(Objects.requireNonNull(groups, "groups must not be null"));
		remainingCandidates = List.copyOf(Objects.requireNonNull(
				remainingCandidates,
				"remainingCandidates must not be null"
		));
	}
}
