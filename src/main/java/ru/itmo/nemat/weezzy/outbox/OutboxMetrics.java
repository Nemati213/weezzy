package ru.itmo.nemat.weezzy.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxMetrics implements MeterBinder {
	private static final String METRIC_NAME = "weezzy.outbox.events";

	private final OutboxEventRepository repository;

	@Override
	public void bindTo(MeterRegistry registry) {
		registerGauge(registry, OutboxEventStatus.PENDING);
		registerGauge(registry, OutboxEventStatus.FAILED);
	}

	private void registerGauge(MeterRegistry registry, OutboxEventStatus status) {
		Gauge.builder(
				METRIC_NAME,
				repository,
				ignored -> repository.countByStatus(status)
		)
				.description("Number of outbox events by status")
				.tag("status", status.name().toLowerCase())
				.register(registry);
	}
}
