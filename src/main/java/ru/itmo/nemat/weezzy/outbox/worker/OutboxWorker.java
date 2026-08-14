package ru.itmo.nemat.weezzy.outbox.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.outbox.config.OutboxProperties;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(
		name = "app.outbox.worker.enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class OutboxWorker {
	private final OutboxEventProcessor processor;
	private final OutboxEventClaimService claimService;
	private final OutboxProperties properties;
	private final Clock clock;
	private final String workerId;

	public OutboxWorker(
			OutboxEventProcessor processor,
			OutboxEventClaimService claimService,
			OutboxProperties properties,
			Clock clock
	) {
		this.processor = processor;
		this.claimService = claimService;
		this.properties = properties;
		this.clock = clock;
		String configuredWorkerId = properties.worker().workerId();
		this.workerId = configuredWorkerId == null || configuredWorkerId.isBlank()
				? UUID.randomUUID().toString()
				: configuredWorkerId;
	}

	@Scheduled(fixedDelayString = "${app.outbox.worker.fixed-delay}")
	public void processBatch() {
		LocalDateTime now = LocalDateTime.now(clock);
		recoverStaleClaims(now);

		var eventIds = claimService.claimBatch(
				workerId,
				properties.worker().batchSize(),
				now
		);
		for (UUID eventId : eventIds) {
			processOne(eventId);
		}
	}

	private void recoverStaleClaims(LocalDateTime now) {
		int recovered = claimService.recoverStaleClaims(
				now.minus(properties.worker().lockTimeout()),
				properties.worker().batchSize(),
				now
		);
		if (recovered > 0) {
			log.warn("Recovered {} stale outbox event claims", recovered);
		}
	}

	private void processOne(UUID eventId) {
		try {
			processor.process(eventId, workerId, LocalDateTime.now(clock));
		} catch (Exception processingException) {
			recordFailure(eventId, processingException);
		}
	}

	private void recordFailure(UUID eventId, Exception processingException) {
		try {
			processor.recordFailure(
					eventId,
					workerId,
					processingException,
					LocalDateTime.now(clock)
			);
			log.warn(
					"Outbox event {} processing failed: {}",
					eventId,
					processingException.getMessage()
			);
		} catch (Exception failureRecordingException) {
			log.error(
					"Could not record failure for outbox event {}",
					eventId,
					failureRecordingException
			);
		}
	}
}
