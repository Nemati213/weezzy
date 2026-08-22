package ru.itmo.nemat.weezzy.lunch.matching;

import java.util.Objects;
import java.util.UUID;

public record MatchingProfilePair(UUID firstProfileId, UUID secondProfileId) {
	public MatchingProfilePair {
		Objects.requireNonNull(firstProfileId, "firstProfileId must not be null");
		Objects.requireNonNull(secondProfileId, "secondProfileId must not be null");
		if (firstProfileId.equals(secondProfileId)) {
			throw new IllegalArgumentException("profile pair must contain two profiles");
		}
		if (firstProfileId.compareTo(secondProfileId) > 0) {
			UUID first = firstProfileId;
			firstProfileId = secondProfileId;
			secondProfileId = first;
		}
	}
}
