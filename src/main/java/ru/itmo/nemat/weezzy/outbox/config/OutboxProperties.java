package ru.itmo.nemat.weezzy.outbox.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
		@Valid @NotNull Worker worker,
		@Valid @NotNull Cleanup cleanup
) {
	public record Worker(
			boolean enabled,
			@NotNull Duration fixedDelay,
			@Min(1) int batchSize,
			@Min(1) int maxAttempts,
			@NotNull Duration lockTimeout,
			@NotEmpty List<@NotNull Duration> retryDelays,
			@Size(max = 100) String workerId
	) {
		@AssertTrue(message = "outbox worker durations must be positive")
		public boolean hasPositiveDurations() {
			return fixedDelay != null
					&& !fixedDelay.isNegative()
					&& !fixedDelay.isZero()
					&& lockTimeout != null
					&& !lockTimeout.isNegative()
					&& !lockTimeout.isZero()
					&& retryDelays != null
					&& retryDelays.stream().allMatch(delay -> delay != null
							&& !delay.isNegative()
							&& !delay.isZero());
		}
	}

	public record Cleanup(
			boolean enabled,
			@NotNull Duration fixedDelay,
			@NotNull Duration retention,
			@Min(1) int batchSize
	) {
		@AssertTrue(message = "outbox cleanup durations must be positive")
		public boolean hasPositiveDurations() {
			return fixedDelay != null
					&& !fixedDelay.isNegative()
					&& !fixedDelay.isZero()
					&& retention != null
					&& !retention.isNegative()
					&& !retention.isZero();
		}
	}
}
