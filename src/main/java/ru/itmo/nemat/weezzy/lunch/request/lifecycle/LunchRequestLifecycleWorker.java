package ru.itmo.nemat.weezzy.lunch.request.lifecycle;

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
	private final LunchProperties properties;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${app.lunch.lifecycle.fixed-delay}")
	public void offerExtensions() {
		try {
			List<UUID> offeredRequestIds = lifecycleService.offerExtensions(
					now(),
					properties.lifecycle().batchSize()
			);
			if (!offeredRequestIds.isEmpty()) {
				log.info(
						"Created {} lunch extension offers",
						offeredRequestIds.size()
				);
			}
		} catch (Exception exception) {
			log.error("Lunch extension offer processing failed", exception);
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), properties.zoneId());
	}
}
