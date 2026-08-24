package ru.itmo.nemat.weezzy.lunch.group.lifecycle;

import io.micrometer.core.instrument.Timer;
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
	private final LunchGroupLifecycleMetrics metrics;
	private final LunchProperties properties;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${app.lunch.lifecycle.fixed-delay}")
	public void processGroups() {
		Timer.Sample sample = metrics.start();
		try {
			LocalDateTime now = now();
			List<UUID> cancelledGroupIds = lifecycleService.cancelInvalidGroups(
					now,
					properties.lifecycle().batchSize()
			);
			metrics.recordCancelledGroups(cancelledGroupIds.size());
			List<UUID> completedGroupIds = lifecycleService.completeDueGroups(
					now,
					properties.lifecycle().batchSize()
			);
			metrics.recordCompletedGroups(completedGroupIds.size());
			if (!cancelledGroupIds.isEmpty()) {
				log.info("Cancelled {} lunch groups", cancelledGroupIds.size());
			}
			if (!completedGroupIds.isEmpty()) {
				log.info("Completed {} lunch groups", completedGroupIds.size());
			}
			metrics.recordRunSuccess(sample);
		} catch (Exception exception) {
			metrics.recordRunFailure(sample);
			log.error("Lunch group lifecycle processing failed", exception);
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), properties.zoneId());
	}
}
