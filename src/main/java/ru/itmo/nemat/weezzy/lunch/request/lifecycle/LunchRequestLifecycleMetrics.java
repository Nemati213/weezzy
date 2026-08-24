package ru.itmo.nemat.weezzy.lunch.request.lifecycle;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class LunchRequestLifecycleMetrics {
	private final Counter successfulRuns;
	private final Counter failedRuns;
	private final Counter expiredRequests;
	private final Counter offeredExtensions;
	private final Timer duration;

	public LunchRequestLifecycleMetrics(MeterRegistry registry) {
		successfulRuns = runCounter(registry, "success");
		failedRuns = runCounter(registry, "failure");
		expiredRequests = Counter.builder(
				"weezzy.lunch.request.lifecycle.requests.expired"
		)
				.description("Number of expired lunch requests")
				.register(registry);
		offeredExtensions = Counter.builder(
				"weezzy.lunch.request.lifecycle.extensions.offered"
		)
				.description("Number of offered lunch request extensions")
				.register(registry);
		duration = Timer.builder("weezzy.lunch.request.lifecycle.duration")
				.description("Lunch request lifecycle run duration")
				.register(registry);
	}

	Timer.Sample start() {
		return Timer.start();
	}

	void recordExpiredRequests(int count) {
		expiredRequests.increment(count);
	}

	void recordOfferedExtensions(int count) {
		offeredExtensions.increment(count);
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
		return Counter.builder("weezzy.lunch.request.lifecycle.runs")
				.description("Number of lunch request lifecycle runs")
				.tag("outcome", outcome)
				.register(registry);
	}
}
