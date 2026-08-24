package ru.itmo.nemat.weezzy.lunch.group.lifecycle;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class LunchGroupLifecycleMetrics {
	private final Counter successfulRuns;
	private final Counter failedRuns;
	private final Counter cancelledGroups;
	private final Counter completedGroups;
	private final Timer duration;

	public LunchGroupLifecycleMetrics(MeterRegistry registry) {
		successfulRuns = runCounter(registry, "success");
		failedRuns = runCounter(registry, "failure");
		cancelledGroups = Counter.builder(
				"weezzy.lunch.group.lifecycle.groups.cancelled"
		)
				.description("Number of cancelled lunch groups")
				.register(registry);
		completedGroups = Counter.builder(
				"weezzy.lunch.group.lifecycle.groups.completed"
		)
				.description("Number of completed lunch groups")
				.register(registry);
		duration = Timer.builder("weezzy.lunch.group.lifecycle.duration")
				.description("Lunch group lifecycle run duration")
				.register(registry);
	}

	Timer.Sample start() {
		return Timer.start();
	}

	void recordCancelledGroups(int count) {
		cancelledGroups.increment(count);
	}

	void recordCompletedGroups(int count) {
		completedGroups.increment(count);
	}

	void recordRunSuccess(Timer.Sample sample) {
		successfulRuns.increment();
		sample.stop(duration);
	}

	void recordRunFailure(Timer.Sample sample) {
		failedRuns.increment();
		sample.stop(duration);
	}

	private Counter runCounter(MeterRegistry registry, String outcome) {
		return Counter.builder("weezzy.lunch.group.lifecycle.runs")
				.description("Number of lunch group lifecycle runs")
				.tag("outcome", outcome)
				.register(registry);
	}
}
