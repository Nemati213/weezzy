package ru.itmo.nemat.weezzy.lunch.chat.cleanup;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class LunchChatCleanupMetrics {
	private final Counter successfulRuns;
	private final Counter failedRuns;
	private final Counter deletedMessages;
	private final Timer duration;

	public LunchChatCleanupMetrics(MeterRegistry registry) {
		successfulRuns = Counter.builder("weezzy.lunch.chat.cleanup.runs")
				.description("Number of lunch chat cleanup runs")
				.tag("outcome", "success")
				.register(registry);
		failedRuns = Counter.builder("weezzy.lunch.chat.cleanup.runs")
				.description("Number of lunch chat cleanup runs")
				.tag("outcome", "failure")
				.register(registry);
		deletedMessages = Counter.builder(
				"weezzy.lunch.chat.cleanup.messages.deleted"
		)
				.description("Number of deleted lunch chat messages")
				.register(registry);
		duration = Timer.builder("weezzy.lunch.chat.cleanup.duration")
				.description("Lunch chat cleanup duration")
				.register(registry);
	}

	Timer.Sample start() {
		return Timer.start();
	}

	void recordSuccess(Timer.Sample sample, int deleted) {
		successfulRuns.increment();
		deletedMessages.increment(deleted);
		sample.stop(duration);
	}

	void recordFailure(Timer.Sample sample) {
		failedRuns.increment();
		sample.stop(duration);
	}
}
