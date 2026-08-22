package ru.itmo.nemat.weezzy.lunch.matching;

import java.time.LocalDateTime;

public interface MatchingStrategy {
	int priority();

	MatchingStrategyResult match(MatchingBucket bucket, LocalDateTime now);
}
