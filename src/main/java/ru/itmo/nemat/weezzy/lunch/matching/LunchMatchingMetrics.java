package ru.itmo.nemat.weezzy.lunch.matching;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class LunchMatchingMetrics {
	private final Counter successfulRuns;
	private final Counter failedRuns;
	private final Counter discoveredBuckets;
	private final Counter claimedBuckets;
	private final Counter skippedBuckets;
	private final Counter failedBuckets;
	private final Counter formedGroups;
	private final Counter matchedParticipants;
	private final Timer duration;

	public LunchMatchingMetrics(MeterRegistry registry) {
		successfulRuns = runCounter(registry, "success");
		failedRuns = runCounter(registry, "failure");
		discoveredBuckets = Counter.builder(
				"weezzy.lunch.matching.buckets.discovered"
		)
				.description("Number of discovered lunch matching buckets")
				.register(registry);
		claimedBuckets = bucketCounter(registry, "claimed");
		skippedBuckets = bucketCounter(registry, "skipped");
		failedBuckets = bucketCounter(registry, "failure");
		formedGroups = Counter.builder("weezzy.lunch.matching.groups.formed")
				.description("Number of groups formed by lunch matching")
				.register(registry);
		matchedParticipants = Counter.builder(
				"weezzy.lunch.matching.participants.matched"
		)
				.description("Number of participants matched into lunch groups")
				.register(registry);
		duration = Timer.builder("weezzy.lunch.matching.duration")
				.description("Lunch matching run duration")
				.register(registry);
	}

	Timer.Sample start() {
		return Timer.start();
	}

	void recordDiscoveredBuckets(int count) {
		discoveredBuckets.increment(count);
	}

	void recordBucketResult(LunchMatchingBucketProcessingResult result) {
		if (!result.claimed()) {
			skippedBuckets.increment();
			return;
		}

		claimedBuckets.increment();
		formedGroups.increment(result.formedGroupCount());
		matchedParticipants.increment(result.matchedCandidateCount());
	}

	void recordBucketFailure() {
		failedBuckets.increment();
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
		return Counter.builder("weezzy.lunch.matching.runs")
				.description("Number of lunch matching runs")
				.tag("outcome", outcome)
				.register(registry);
	}

	private Counter bucketCounter(MeterRegistry registry, String outcome) {
		return Counter.builder("weezzy.lunch.matching.buckets.processed")
				.description("Number of processed lunch matching buckets")
				.tag("outcome", outcome)
				.register(registry);
	}
}
