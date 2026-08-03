package ru.itmo.nemat.weezzy.security.ratelimit;

import java.time.Duration;

public record AuthRateLimitDecision(
		boolean allowed,
		int limit,
		int remaining,
		Duration retryAfter
) {
}
