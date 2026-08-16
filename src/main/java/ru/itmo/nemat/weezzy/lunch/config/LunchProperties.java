package ru.itmo.nemat.weezzy.lunch.config;

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
		@NotNull Duration extensionResponseTimeout
) {
	@AssertTrue(message = "lunch time window and durations must be valid")
	public boolean hasValidTimeConfiguration() {
		return windowStart != null
				&& windowEnd != null
				&& windowStart.isBefore(windowEnd)
				&& isPositiveWholeMinutes(slotInterval)
				&& isPositiveWholeMinutes(extensionDuration)
				&& isPositiveWholeMinutes(extensionResponseTimeout);
	}

	private boolean isPositiveWholeMinutes(Duration duration) {
		return duration != null
				&& !duration.isZero()
				&& !duration.isNegative()
				&& duration.toSecondsPart() == 0
				&& duration.toNanosPart() == 0;
	}
}
