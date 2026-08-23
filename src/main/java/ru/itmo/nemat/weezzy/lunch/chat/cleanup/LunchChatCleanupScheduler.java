package ru.itmo.nemat.weezzy.lunch.chat.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.lunch.config.LunchProperties;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
		name = "app.lunch.chat.cleanup.enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class LunchChatCleanupScheduler {
	private final LunchChatCleanupService cleanupService;
	private final LunchProperties properties;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${app.lunch.chat.cleanup.fixed-delay}")
	public void cleanup() {
		int deleted = cleanupService.deleteExpired(
				LocalDateTime.now(clock),
				properties.chat().cleanup().batchSize()
		);
		if (deleted > 0) {
			log.info("Deleted {} expired lunch chat messages", deleted);
		}
	}
}
