package ru.itmo.nemat.weezzy.lunch.request.lifecycle;

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
public class LunchRequestLifecycleWorker {
	private final LunchRequestLifecycleService lifecycleService;
	private final LunchRequestLifecycleMetrics metrics;
	private final LunchProperties properties;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${app.lunch.lifecycle.fixed-delay}")
	public void processLifecycle() {
		Timer.Sample sample = metrics.start();
		try {
			LocalDateTime now = now();
			List<UUID> expiredRequestIds = lifecycleService.expireRequests(
					now,
					properties.lifecycle().batchSize()
			);
			metrics.recordExpiredRequests(expiredRequestIds.size());
			List<UUID> offeredRequestIds = lifecycleService.offerExtensions(
					now,
					properties.lifecycle().batchSize()
			);
			metrics.recordOfferedExtensions(offeredRequestIds.size());
			if (!expiredRequestIds.isEmpty()) {
				log.info(
						"Expired {} lunch requests",
						expiredRequestIds.size()
				);
			}
			if (!offeredRequestIds.isEmpty()) {
				log.info(
						"Created {} lunch extension offers",
						offeredRequestIds.size()
				);
			}
			metrics.recordRunSuccess(sample);
		} catch (Exception exception) {
			metrics.recordRunFailure(sample);
			log.error("Lunch request lifecycle processing failed", exception);
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), properties.zoneId());
	}
}
