package ru.itmo.nemat.weezzy.outbox.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventClaimService {
	private static final String STALE_CLAIM_ERROR =
			"Worker lock expired before processing completed";

	private final OutboxEventRepository repository;
	private final OutboxRetryPolicy retryPolicy;

	@Transactional
	public List<UUID> claimBatch(
			String workerId,
			int batchSize,
			LocalDateTime now
	) {
		List<OutboxEvent> events = repository.findPendingForUpdate(now, batchSize);
		events.forEach(event -> event.claim(workerId, now));
		return events.stream().map(OutboxEvent::getId).toList();
	}

	@Transactional
	public int recoverStaleClaims(
			LocalDateTime lockedBefore,
			int batchSize,
			LocalDateTime now
	) {
		List<OutboxEvent> events = repository.findStaleProcessingForUpdate(
				lockedBefore,
				batchSize
		);
		events.forEach(event -> recoverStaleClaim(event, now));
		return events.size();
	}

	private void recoverStaleClaim(OutboxEvent event, LocalDateTime now) {
		if (retryPolicy.attemptsExhausted(event.getAttemptCount())) {
			event.markFailed(STALE_CLAIM_ERROR, now);
			return;
		}

		event.scheduleRetry(
				STALE_CLAIM_ERROR,
				retryPolicy.nextAttemptAt(event.getAttemptCount(), now),
				now
		);
	}
}
