package ru.itmo.nemat.weezzy.team.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TeamApplicationPropertiesTests {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(TeamApplicationConfiguration.class);

	@Test
	void defaultsToTwentyFourHours() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(TeamApplicationProperties.class).reapplicationCooldown())
					.isEqualTo(Duration.ofHours(24));
		});
	}

	@Test
	void acceptsConfiguredCooldown() {
		runner.withPropertyValues("app.team.application.reapplication-cooldown=48h")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(TeamApplicationProperties.class).reapplicationCooldown())
							.isEqualTo(Duration.ofHours(48));
				});
	}

	@ParameterizedTest
	@ValueSource(strings = {"0s", "-1h"})
	void rejectsNonPositiveCooldown(String duration) {
		runner.withPropertyValues("app.team.application.reapplication-cooldown=" + duration)
				.run(context -> assertThat(context).hasFailed());
	}
}
