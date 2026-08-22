package ru.itmo.nemat.weezzy.lunch.group.lifecycle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.lunch.config.LunchProperties;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
		name = "app.lunch.lifecycle.enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class LunchGroupLifecycleWorker {
	private final LunchGroupLifecycleService lifecycleService;
	private final LunchProperties properties;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${app.lunch.lifecycle.fixed-delay}")
	public void processGroups() {
		try {
			LocalDateTime now = now();
			List<UUID> cancelledGroupIds = lifecycleService.cancelInvalidGroups(
					now,
					properties.lifecycle().batchSize()
			);
			List<UUID> completedGroupIds = lifecycleService.completeDueGroups(
					now,
					properties.lifecycle().batchSize()
			);
			if (!cancelledGroupIds.isEmpty()) {
				log.info("Cancelled {} lunch groups", cancelledGroupIds.size());
			}
			if (!completedGroupIds.isEmpty()) {
				log.info("Completed {} lunch groups", completedGroupIds.size());
			}
		} catch (Exception exception) {
			log.error("Lunch group lifecycle processing failed", exception);
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), properties.zoneId());
	}
}
