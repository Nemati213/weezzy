package ru.itmo.nemat.weezzy.team.application;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.team.application")
public record TeamApplicationProperties(
		@NotNull @DefaultValue("24h") Duration reapplicationCooldown
) {
	@AssertTrue(message = "team application reapplication cooldown must be positive")
	public boolean isReapplicationCooldownPositive() {
		return reapplicationCooldown != null
				&& !reapplicationCooldown.isZero() && !reapplicationCooldown.isNegative();
	}
}
