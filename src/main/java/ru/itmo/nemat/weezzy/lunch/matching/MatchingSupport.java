package ru.itmo.nemat.weezzy.lunch.matching;

import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class MatchingSupport {
	static final Comparator<MatchingCandidate> CANDIDATE_ORDER = Comparator
			.comparing(MatchingCandidate::createdAt)
			.thenComparing(MatchingCandidate::requestId);

	private MatchingSupport() {
	}

	static List<MatchingGroup> maximizeGroups(
			List<MatchingCandidate> candidates,
			LunchTopic topic
	) {
		List<MatchingCandidate> ordered = candidates.stream()
				.sorted(CANDIDATE_ORDER)
				.toList();
		List<Integer> groupSizes = maximizeGroupSizes(ordered.size());
		List<MatchingGroup> groups = new ArrayList<>(groupSizes.size());
		int offset = 0;
		for (int groupSize : groupSizes) {
			groups.add(new MatchingGroup(
					topic,
					ordered.subList(offset, offset + groupSize)
			));
			offset += groupSize;
		}
		return groups;
	}

	static MatchingStrategyResult result(
			List<MatchingCandidate> input,
			List<MatchingGroup> groups
	) {
		List<MatchingGroup> orderedGroups = groups.stream()
				.sorted(Comparator
						.comparing(
								(MatchingGroup group) -> group.candidates().getFirst(),
								CANDIDATE_ORDER
						)
						.thenComparing(group -> group.topic().name()))
				.toList();
		Set<UUID> matchedRequestIds = new HashSet<>();
		orderedGroups.forEach(group -> matchedRequestIds.addAll(group.requestIds()));
		List<MatchingCandidate> remaining = input.stream()
				.filter(candidate -> !matchedRequestIds.contains(candidate.requestId()))
				.sorted(CANDIDATE_ORDER)
				.toList();
		return new MatchingStrategyResult(orderedGroups, remaining);
	}

	private static List<Integer> maximizeGroupSizes(int candidateCount) {
		for (int matched = candidateCount; matched >= 3; matched--) {
			for (int groupsOfFour = matched / 4; groupsOfFour >= 0; groupsOfFour--) {
				int remaining = matched - groupsOfFour * 4;
				if (remaining % 3 == 0) {
					List<Integer> sizes = new ArrayList<>();
					for (int index = 0; index < groupsOfFour; index++) {
						sizes.add(4);
					}
					for (int index = 0; index < remaining / 3; index++) {
						sizes.add(3);
					}
					return sizes;
				}
			}
		}
		return List.of();
	}
}
