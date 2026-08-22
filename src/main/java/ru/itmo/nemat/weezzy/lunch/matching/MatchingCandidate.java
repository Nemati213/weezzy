package ru.itmo.nemat.weezzy.lunch.matching;

import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record MatchingCandidate(
		UUID requestId,
		UUID profileId,
		LunchTopic topic,
		LocalDateTime createdAt
) {
	public MatchingCandidate {
		Objects.requireNonNull(requestId, "requestId must not be null");
		Objects.requireNonNull(profileId, "profileId must not be null");
		Objects.requireNonNull(topic, "topic must not be null");
		Objects.requireNonNull(createdAt, "createdAt must not be null");
	}
}
