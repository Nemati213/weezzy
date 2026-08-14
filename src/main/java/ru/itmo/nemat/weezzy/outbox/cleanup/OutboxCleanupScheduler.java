package ru.itmo.nemat.weezzy.outbox.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.outbox.config.OutboxProperties;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
		name = "app.outbox.cleanup.enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class OutboxCleanupScheduler {
	private final OutboxCleanupService cleanupService;
	private final OutboxProperties properties;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${app.outbox.cleanup.fixed-delay}")
	public void cleanup() {
		int deleted = cleanupService.deleteProcessedBefore(
				LocalDateTime.now(clock).minus(properties.cleanup().retention()),
				properties.cleanup().batchSize()
		);
		if (deleted > 0) {
			log.info("Deleted {} processed outbox events", deleted);
		}
	}
}
