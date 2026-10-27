package ru.itmo.nemat.weezzy.team.recommendation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.team-recommendation")
public record TeamRecommendationProperties(
		@NotNull @DefaultValue("3d") Duration impressionCooldown,
		@NotNull @DefaultValue("24h") Duration sessionTtl,
		@NotNull @DefaultValue("30d") Duration impressionRetention,
		@Positive @DefaultValue("10") int maxActiveSessionsPerUser,
		@Valid @NotNull @DefaultValue Cleanup cleanup
) {
	@AssertTrue(message = "recommendation durations must be positive")
	public boolean isDurationConfigurationValid() {
		return positive(impressionCooldown) && positive(sessionTtl)
				&& positive(impressionRetention);
	}

	@AssertTrue(message = "impression retention must cover session TTL and cooldown")
	public boolean isRetentionSufficient() {
		return impressionRetention != null && sessionTtl != null && impressionCooldown != null
				&& impressionRetention.compareTo(sessionTtl) >= 0
				&& impressionRetention.compareTo(impressionCooldown) >= 0;
	}

	private static boolean positive(Duration duration) {
		return duration != null && !duration.isZero() && !duration.isNegative();
	}

	public record Cleanup(
			@DefaultValue("true") boolean enabled,
			@NotNull @DefaultValue("15m") Duration interval,
			@Positive @DefaultValue("500") int batchSize,
			@Positive @DefaultValue("10") int maxBatchesPerRun
	) {
		@AssertTrue(message = "recommendation cleanup interval must be positive")
		public boolean isIntervalPositive() {
			return positive(interval);
		}
	}
}
