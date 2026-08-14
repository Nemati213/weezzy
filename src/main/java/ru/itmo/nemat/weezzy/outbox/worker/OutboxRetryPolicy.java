package ru.itmo.nemat.weezzy.outbox.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.outbox.config.OutboxProperties;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OutboxRetryPolicy {
	private final OutboxProperties properties;

	public boolean attemptsExhausted(int attemptCount) {
		return attemptCount >= properties.worker().maxAttempts();
	}

	public LocalDateTime nextAttemptAt(int attemptCount, LocalDateTime now) {
		return now.plus(delayForAttempt(attemptCount));
	}

	private Duration delayForAttempt(int attemptCount) {
		var retryDelays = properties.worker().retryDelays();
		int index = Math.min(Math.max(attemptCount - 1, 0), retryDelays.size() - 1);
		return retryDelays.get(index);
	}
}
