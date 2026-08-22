package ru.itmo.nemat.weezzy.lunch.matching;

import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MatchingGroup(
		LunchTopic topic,
		List<MatchingCandidate> candidates
) {
	public MatchingGroup {
		Objects.requireNonNull(topic, "topic must not be null");
		candidates = List.copyOf(Objects.requireNonNull(
				candidates,
				"candidates must not be null"
		));
		if (candidates.size() < 2 || candidates.size() > 4) {
			throw new IllegalArgumentException(
					"matching group size must be between 2 and 4"
			);
		}
		if (new HashSet<>(candidates.stream()
				.map(MatchingCandidate::requestId)
				.toList()).size() != candidates.size()) {
			throw new IllegalArgumentException(
					"matching group candidates must be unique"
			);
		}
		if (new HashSet<>(candidates.stream()
				.map(MatchingCandidate::profileId)
				.toList()).size() != candidates.size()) {
			throw new IllegalArgumentException(
					"matching group profiles must be unique"
			);
		}
	}

	public List<UUID> requestIds() {
		return candidates.stream().map(MatchingCandidate::requestId).toList();
	}
}
