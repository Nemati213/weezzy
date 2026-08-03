package ru.itmo.nemat.weezzy.recommendation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.recommendation")
public record RecommendationProperties(
		@Valid @NotNull Weights weights,
		@NotNull Duration impressionCooldown
) {
	public record Weights(
			@PositiveOrZero int skill,
			@PositiveOrZero int interest,
			@PositiveOrZero int goal
	) {
	}
}
