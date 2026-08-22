package ru.itmo.nemat.weezzy.lunch.matching;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MatchingBucket(
		UUID locationId,
		LocalDateTime timeSlot,
		List<MatchingCandidate> candidates
) {
	public MatchingBucket {
		Objects.requireNonNull(locationId, "locationId must not be null");
		Objects.requireNonNull(timeSlot, "timeSlot must not be null");
		candidates = List.copyOf(Objects.requireNonNull(
				candidates,
				"candidates must not be null"
		));
		if (new HashSet<>(candidates.stream()
				.map(MatchingCandidate::requestId)
				.toList()).size() != candidates.size()) {
			throw new IllegalArgumentException("request IDs must be unique");
		}
		if (new HashSet<>(candidates.stream()
				.map(MatchingCandidate::profileId)
				.toList()).size() != candidates.size()) {
			throw new IllegalArgumentException("profile IDs must be unique");
		}
	}

	public MatchingBucket withCandidates(List<MatchingCandidate> remaining) {
		return new MatchingBucket(locationId, timeSlot, remaining);
	}
}
