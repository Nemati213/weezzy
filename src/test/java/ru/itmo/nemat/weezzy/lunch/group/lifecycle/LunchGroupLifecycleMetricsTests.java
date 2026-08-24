package ru.itmo.nemat.weezzy.lunch.group.lifecycle;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LunchGroupLifecycleMetricsTests {
	private static final ZoneId ZONE_ID = ZoneId.of("Europe/Moscow");
	private static final LocalDateTime NOW = LocalDateTime.of(
			2026,
			8,
			24,
			12,
			0
	);
	private static final int BATCH_SIZE = 37;

	@Test
	void workerRecordsSuccessfulRunAndProcessedGroups() {
		LunchGroupLifecycleService service = mock(LunchGroupLifecycleService.class);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		when(service.cancelInvalidGroups(NOW, BATCH_SIZE)).thenReturn(List.of(
				UUID.randomUUID(),
				UUID.randomUUID()
		));
		when(service.completeDueGroups(NOW, BATCH_SIZE)).thenReturn(List.of(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID()
		));
		LunchGroupLifecycleWorker worker = worker(service, registry);

		worker.processGroups();

		assertRunCounter(registry, "success", 1);
		assertThat(counter(
				registry,
				"weezzy.lunch.group.lifecycle.groups.cancelled"
		)).isEqualTo(2);
		assertThat(counter(
				registry,
				"weezzy.lunch.group.lifecycle.groups.completed"
		)).isEqualTo(3);
		assertThat(registry.get("weezzy.lunch.group.lifecycle.duration")
				.timer()
				.count()).isEqualTo(1);
		verify(service).cancelInvalidGroups(NOW, BATCH_SIZE);
		verify(service).completeDueGroups(NOW, BATCH_SIZE);
	}

	@Test
	void failureRecordsCompletedWorkAndFailedRun() {
		LunchGroupLifecycleService service = mock(LunchGroupLifecycleService.class);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		when(service.cancelInvalidGroups(NOW, BATCH_SIZE)).thenReturn(List.of(
				UUID.randomUUID(),
				UUID.randomUUID()
		));
		when(service.completeDueGroups(NOW, BATCH_SIZE))
				.thenThrow(new IllegalStateException("database unavailable"));
		LunchGroupLifecycleWorker worker = worker(service, registry);

		worker.processGroups();

		assertRunCounter(registry, "failure", 1);
		assertThat(counter(
				registry,
				"weezzy.lunch.group.lifecycle.groups.cancelled"
		)).isEqualTo(2);
		assertThat(counter(
				registry,
				"weezzy.lunch.group.lifecycle.groups.completed"
		)).isZero();
		assertThat(registry.get("weezzy.lunch.group.lifecycle.duration")
				.timer()
				.count()).isEqualTo(1);
	}

	private LunchGroupLifecycleWorker worker(
			LunchGroupLifecycleService service,
			SimpleMeterRegistry registry
	) {
		Clock clock = Clock.fixed(
				Instant.parse("2026-08-24T09:00:00Z"),
				ZONE_ID
		);
		return new LunchGroupLifecycleWorker(
				service,
				new LunchGroupLifecycleMetrics(registry),
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
						BATCH_SIZE
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

	private void assertRunCounter(
			SimpleMeterRegistry registry,
			String outcome,
			double expected
	) {
		assertThat(registry.get("weezzy.lunch.group.lifecycle.runs")
				.tag("outcome", outcome)
				.counter()
				.count()).isEqualTo(expected);
	}

	private double counter(SimpleMeterRegistry registry, String name) {
		return registry.get(name).counter().count();
	}
}
