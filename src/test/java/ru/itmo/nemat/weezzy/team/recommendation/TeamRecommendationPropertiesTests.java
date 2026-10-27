package ru.itmo.nemat.weezzy.team.recommendation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TeamRecommendationPropertiesTests {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withInitializer(context -> context.getEnvironment().getPropertySources()
					.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME))
			.withUserConfiguration(TeamRecommendationConfiguration.class);

	@Test
	void bindsAgreedDefaultsIncludingNestedCleanup() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			var properties = context.getBean(TeamRecommendationProperties.class);
			assertThat(properties.impressionCooldown()).isEqualTo(Duration.ofDays(3));
			assertThat(properties.sessionTtl()).isEqualTo(Duration.ofHours(24));
			assertThat(properties.impressionRetention()).isEqualTo(Duration.ofDays(30));
			assertThat(properties.maxActiveSessionsPerUser()).isEqualTo(10);
			assertThat(properties.cleanup().enabled()).isTrue();
			assertThat(properties.cleanup().interval()).isEqualTo(Duration.ofMinutes(15));
			assertThat(properties.cleanup().batchSize()).isEqualTo(500);
			assertThat(properties.cleanup().maxBatchesPerRun()).isEqualTo(10);
		});
	}

	@Test
	void acceptsOverridesAndRetentionEqualToBothDurations() {
		runner.withPropertyValues("app.team-recommendation.impression-cooldown=2d",
				"app.team-recommendation.session-ttl=2d",
				"app.team-recommendation.impression-retention=2d",
				"app.team-recommendation.max-active-sessions-per-user=4",
				"app.team-recommendation.cleanup.enabled=false",
				"app.team-recommendation.cleanup.interval=1m",
				"app.team-recommendation.cleanup.batch-size=100",
				"app.team-recommendation.cleanup.max-batches-per-run=2").run(context -> {
			assertThat(context).hasNotFailed();
			var properties = context.getBean(TeamRecommendationProperties.class);
			assertThat(properties.impressionCooldown()).isEqualTo(Duration.ofDays(2));
			assertThat(properties.sessionTtl()).isEqualTo(Duration.ofDays(2));
			assertThat(properties.impressionRetention()).isEqualTo(Duration.ofDays(2));
			assertThat(properties.maxActiveSessionsPerUser()).isEqualTo(4);
			assertThat(properties.cleanup().enabled()).isFalse();
			assertThat(properties.cleanup().interval()).isEqualTo(Duration.ofMinutes(1));
			assertThat(properties.cleanup().batchSize()).isEqualTo(100);
			assertThat(properties.cleanup().maxBatchesPerRun()).isEqualTo(2);
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {"impression-cooldown=0s", "impression-cooldown=-1d",
			"session-ttl=0s", "session-ttl=-1h", "impression-retention=0s",
			"impression-retention=-1d", "impression-retention=2d", "session-ttl=31d",
			"max-active-sessions-per-user=0", "max-active-sessions-per-user=-1",
			"cleanup.interval=0s", "cleanup.interval=-1m", "cleanup.batch-size=0",
			"cleanup.batch-size=-1", "cleanup.max-batches-per-run=0",
			"cleanup.max-batches-per-run=-1"})
	void rejectsInvalidDurationsRetentionAndLimits(String property) {
		runner.withPropertyValues("app.team-recommendation." + property)
				.run(context -> assertThat(context).hasFailed());
	}
}
