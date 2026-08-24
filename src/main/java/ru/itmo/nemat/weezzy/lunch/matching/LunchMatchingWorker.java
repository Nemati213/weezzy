package ru.itmo.nemat.weezzy.lunch.matching;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.lunch.config.LunchProperties;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
		name = "app.lunch.matching.enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class LunchMatchingWorker {
	private final LunchMatchingRepository matchingRepository;
	private final LunchMatchingBucketProcessor bucketProcessor;
	private final LunchMatchingMetrics metrics;
	private final LunchProperties properties;
	private final Clock clock;

	@Scheduled(fixedDelayString = "${app.lunch.matching.fixed-delay}")
	public void processBuckets() {
		Timer.Sample sample = metrics.start();
		try {
			LocalDateTime discoveryTime = now();
			List<MatchingBucketKey> bucketKeys = matchingRepository.findBucketKeys(
					discoveryTime,
					PageRequest.of(0, properties.matching().bucketBatchSize())
			);
			metrics.recordDiscoveredBuckets(bucketKeys.size());
			for (MatchingBucketKey bucketKey : bucketKeys) {
				processOne(bucketKey);
			}
			metrics.recordRunSuccess(sample);
		} catch (RuntimeException | Error exception) {
			metrics.recordRunFailure(sample);
			throw exception;
		}
	}

	private void processOne(MatchingBucketKey bucketKey) {
		try {
			LunchMatchingBucketProcessingResult result = bucketProcessor.process(
					bucketKey,
					now()
			);
			metrics.recordBucketResult(result);
			if (result.claimed() && result.formedGroupCount() > 0) {
				log.info(
						"Formed {} lunch groups with {} participants for bucket {} at {}",
						result.formedGroupCount(),
						result.matchedCandidateCount(),
						bucketKey.locationId(),
						bucketKey.timeSlot()
				);
			}
		} catch (Exception exception) {
			metrics.recordBucketFailure();
			log.error(
					"Lunch matching failed for bucket {} at {}",
					bucketKey.locationId(),
					bucketKey.timeSlot(),
					exception
			);
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), properties.zoneId());
	}
}
