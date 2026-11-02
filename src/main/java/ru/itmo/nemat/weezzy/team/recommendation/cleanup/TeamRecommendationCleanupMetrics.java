package ru.itmo.nemat.weezzy.team.recommendation.cleanup;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class TeamRecommendationCleanupMetrics {
	private static final String PREFIX = "weezzy.team.recommendation.cleanup.";
	private final MeterRegistry registry;
	private final Counter success;
	private final Counter failure;
	private final Counter batches;
	private final Counter sessions;
	private final Counter entries;
	private final Counter impressions;
	private final Timer duration;

	public TeamRecommendationCleanupMetrics(MeterRegistry registry) {
		this.registry = registry;
		success = registry.counter(PREFIX + "runs", "outcome", "success");
		failure = registry.counter(PREFIX + "runs", "outcome", "failure");
		batches = registry.counter(PREFIX + "batches");
		sessions = registry.counter(PREFIX + "deleted", "kind", "session");
		entries = registry.counter(PREFIX + "deleted", "kind", "entry");
		impressions = registry.counter(PREFIX + "deleted", "kind", "impression");
		duration = registry.timer(PREFIX + "duration");
	}

	Timer.Sample start() {
		return Timer.start(registry);
	}

	void recordBatch(TeamRecommendationCleanupResult result) {
		batches.increment();
		sessions.increment(result.sessions());
		entries.increment(result.entries());
		impressions.increment(result.impressions());
	}

	void recordSuccess(Timer.Sample sample) {
		success.increment();
		sample.stop(duration);
	}

	void recordFailure(Timer.Sample sample) {
		failure.increment();
		sample.stop(duration);
	}
}
