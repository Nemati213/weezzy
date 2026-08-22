package ru.itmo.nemat.weezzy.lunch.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

@Validated
@ConfigurationProperties(prefix = "app.lunch")
public record LunchProperties(
		@NotNull ZoneId zoneId,
		@NotNull LocalTime windowStart,
		@NotNull LocalTime windowEnd,
		@NotNull Duration slotInterval,
		@NotNull Duration extensionDuration,
		@Min(0) int maxExtensions,
		@NotNull Duration extensionResponseTimeout,
		@NotNull Duration groupDuration,
		@Valid @NotNull Matching matching,
		@Valid @NotNull Lifecycle lifecycle
) {
	public record Matching(
			boolean enabled,
			@NotNull Duration fixedDelay,
			@Min(1) int bucketBatchSize
	) {
		@AssertTrue(message = "lunch matching fixed delay must be positive")
		public boolean hasPositiveFixedDelay() {
			return fixedDelay != null
					&& !fixedDelay.isZero()
					&& !fixedDelay.isNegative();
		}
	}

	public record Lifecycle(
			boolean enabled,
			@NotNull Duration fixedDelay,
			@Min(1) int batchSize
	) {
		@AssertTrue(message = "lunch lifecycle fixed delay must be positive")
		public boolean hasPositiveFixedDelay() {
			return fixedDelay != null
					&& !fixedDelay.isZero()
					&& !fixedDelay.isNegative();
		}
	}

	@AssertTrue(message = "lunch time window and durations must be valid")
	public boolean hasValidTimeConfiguration() {
		return windowStart != null
				&& windowEnd != null
				&& windowStart.isBefore(windowEnd)
				&& isPositiveWholeMinutes(slotInterval)
				&& isPositiveWholeMinutes(extensionDuration)
				&& isPositiveWholeMinutes(extensionResponseTimeout)
				&& isPositiveWholeMinutes(groupDuration)
				&& extensionDuration.compareTo(
						Duration.between(windowStart, windowEnd)
				) <= 0;
	}

	private boolean isPositiveWholeMinutes(Duration duration) {
		return duration != null
				&& !duration.isZero()
				&& !duration.isNegative()
				&& duration.toSecondsPart() == 0
				&& duration.toNanosPart() == 0;
	}
}
