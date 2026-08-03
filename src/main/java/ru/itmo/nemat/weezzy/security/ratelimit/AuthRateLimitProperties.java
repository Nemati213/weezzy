package ru.itmo.nemat.weezzy.security.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.security.rate-limit")
public record AuthRateLimitProperties(
		boolean enabled,
		@Valid @NotNull Policy login,
		@Valid @NotNull Policy register
) {
	public record Policy(
			@Min(1) int capacity,
			@NotNull Duration window
	) {
		@AssertTrue(message = "window must be positive")
		public boolean isWindowPositive() {
			return window != null && !window.isZero() && !window.isNegative();
		}
	}
}
