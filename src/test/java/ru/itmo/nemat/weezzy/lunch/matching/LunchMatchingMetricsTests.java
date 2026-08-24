package ru.itmo.nemat.weezzy.lunch.matching;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import ru.itmo.nemat.weezzy.lunch.config.LunchProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LunchMatchingMetricsTests {
	private static final ZoneId ZONE_ID = ZoneId.of("Europe/Moscow");
	private static final LocalDateTime NOW = LocalDateTime.of(
			2026,
			8,
			24,
			12,
			0
	);

	@Test
	void workerRecordsSuccessfulRunAndBucketResults() {
		LunchMatchingRepository repository = mock(LunchMatchingRepository.class);
		LunchMatchingBucketProcessor processor = mock(
				LunchMatchingBucketProcessor.class
		);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		MatchingBucketKey claimedBucket = bucket(13);
		MatchingBucketKey skippedBucket = bucket(14);
		when(repository.findBucketKeys(eq(NOW), any(Pageable.class)))
				.thenReturn(List.of(claimedBucket, skippedBucket));
		when(processor.process(claimedBucket, NOW)).thenReturn(
				new LunchMatchingBucketProcessingResult(true, 2, 7, 1)
		);
		when(processor.process(skippedBucket, NOW)).thenReturn(
				LunchMatchingBucketProcessingResult.notClaimed()
		);
		LunchMatchingWorker worker = worker(repository, processor, registry);

		worker.processBuckets();

		assertCounter(registry, "weezzy.lunch.matching.runs", "success", 1);
		assertCounter(
				registry,
				"weezzy.lunch.matching.buckets.processed",
				"claimed",
				1
		);
		assertCounter(
				registry,
				"weezzy.lunch.matching.buckets.processed",
				"skipped",
				1
		);
		assertThat(counter(
				registry,
				"weezzy.lunch.matching.buckets.discovered"
		)).isEqualTo(2);
		assertThat(counter(registry, "weezzy.lunch.matching.groups.formed"))
				.isEqualTo(2);
		assertThat(counter(
				registry,
				"weezzy.lunch.matching.participants.matched"
		)).isEqualTo(7);
		assertThat(registry.get("weezzy.lunch.matching.duration")
				.timer()
				.count()).isEqualTo(1);
		verify(repository).findBucketKeys(eq(NOW), any(Pageable.class));
	}

	@Test
	void bucketFailureIsRecordedWithoutStoppingOtherBuckets() {
		LunchMatchingRepository repository = mock(LunchMatchingRepository.class);
		LunchMatchingBucketProcessor processor = mock(
				LunchMatchingBucketProcessor.class
		);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		MatchingBucketKey failedBucket = bucket(13);
		MatchingBucketKey successfulBucket = bucket(14);
		when(repository.findBucketKeys(eq(NOW), any(Pageable.class)))
				.thenReturn(List.of(failedBucket, successfulBucket));
		when(processor.process(failedBucket, NOW))
				.thenThrow(new IllegalStateException("bucket failed"));
		when(processor.process(successfulBucket, NOW)).thenReturn(
				new LunchMatchingBucketProcessingResult(true, 1, 4, 0)
		);
		LunchMatchingWorker worker = worker(repository, processor, registry);

		worker.processBuckets();

		assertCounter(
				registry,
				"weezzy.lunch.matching.buckets.processed",
				"failure",
				1
		);
		assertCounter(
				registry,
				"weezzy.lunch.matching.buckets.processed",
				"claimed",
				1
		);
		assertCounter(registry, "weezzy.lunch.matching.runs", "success", 1);
		assertThat(counter(registry, "weezzy.lunch.matching.groups.formed"))
				.isEqualTo(1);
	}

	@Test
	void discoveryFailureRecordsFailedRunAndDuration() {
		LunchMatchingRepository repository = mock(LunchMatchingRepository.class);
		LunchMatchingBucketProcessor processor = mock(
				LunchMatchingBucketProcessor.class
		);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		when(repository.findBucketKeys(eq(NOW), any(Pageable.class)))
				.thenThrow(new IllegalStateException("database unavailable"));
		LunchMatchingWorker worker = worker(repository, processor, registry);

		assertThatThrownBy(worker::processBuckets)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("database unavailable");

		assertCounter(registry, "weezzy.lunch.matching.runs", "failure", 1);
		assertThat(registry.get("weezzy.lunch.matching.duration")
				.timer()
				.count()).isEqualTo(1);
	}

	private LunchMatchingWorker worker(
			LunchMatchingRepository repository,
			LunchMatchingBucketProcessor processor,
			SimpleMeterRegistry registry
	) {
		Clock clock = Clock.fixed(
				Instant.parse("2026-08-24T09:00:00Z"),
				ZONE_ID
		);
		return new LunchMatchingWorker(
				repository,
				processor,
				new LunchMatchingMetrics(registry),
				properties(),
				clock
		);
	}

	private LunchProperties properties() {
		return new LunchProperties(
				ZONE_ID,
				LocalTime.of(12, 0),
				LocalTime.of(15, 0),
				Duration.ofMinutes(15),
				Duration.ofMinutes(10),
				2,
				Duration.ofMinutes(5),
				Duration.ofHours(1),
				new LunchProperties.Matching(
						false,
						Duration.ofMinutes(1),
						50
				),
				new LunchProperties.Lifecycle(
						false,
						Duration.ofMinutes(1),
						100
				),
				new LunchProperties.Chat(
						Duration.ofDays(7),
						new LunchProperties.Cleanup(
								false,
								Duration.ofHours(1),
								100
						)
				)
		);
	}

	private MatchingBucketKey bucket(int hour) {
		return new MatchingBucketKey(
				UUID.randomUUID(),
				LocalDateTime.of(2026, 8, 24, hour, 0)
		);
	}

	private void assertCounter(
			SimpleMeterRegistry registry,
			String name,
			String outcome,
			double expected
	) {
		assertThat(registry.get(name)
				.tag("outcome", outcome)
				.counter()
				.count()).isEqualTo(expected);
	}

	private double counter(SimpleMeterRegistry registry, String name) {
		return registry.get(name).counter().count();
	}
}
