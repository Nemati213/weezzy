package ru.itmo.nemat.weezzy.lunch.chat.cleanup;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import ru.itmo.nemat.weezzy.lunch.config.LunchProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LunchChatCleanupUnitTests {
	private static final ZoneId ZONE_ID = ZoneId.of("Europe/Moscow");

	@Test
	void schedulerUsesConfiguredBatchSizeAndApplicationClock() {
		LunchChatCleanupService cleanupService = mock(LunchChatCleanupService.class);
		LunchProperties properties = properties(
				Duration.ofDays(7),
				Duration.ofHours(1),
				37
		);
		Clock clock = Clock.fixed(
				Instant.parse("2026-08-23T09:00:00Z"),
				ZONE_ID
		);
		LunchChatCleanupScheduler scheduler = new LunchChatCleanupScheduler(
				cleanupService,
				properties,
				clock
		);

		scheduler.cleanup();

		verify(cleanupService).deleteExpired(
				LocalDateTime.of(2026, 8, 23, 12, 0),
				37
		);
	}

	@Test
	void serviceRecordsFailedRunAndDuration() {
		LunchChatCleanupRepository repository = mock(
				LunchChatCleanupRepository.class
		);
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		LunchChatCleanupService service = new LunchChatCleanupService(
				repository,
				new LunchChatCleanupMetrics(registry),
				properties(Duration.ofDays(7), Duration.ofHours(1), 100)
		);
		LocalDateTime now = LocalDateTime.of(2026, 8, 30, 12, 0);
		when(repository.deleteExpiredBatch(now.minusDays(7), 100))
				.thenThrow(new IllegalStateException("database unavailable"));

		assertThatThrownBy(() -> service.deleteExpired(now, 100))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("database unavailable");
		assertThat(registry.get("weezzy.lunch.chat.cleanup.runs")
				.tag("outcome", "failure")
				.counter()
				.count()).isEqualTo(1);
		assertThat(registry.get("weezzy.lunch.chat.cleanup.duration")
				.timer()
				.count()).isEqualTo(1);
	}

	@Test
	void chatRetentionAndCleanupSettingsMustBePositive() {
		Validator validator = Validation.buildDefaultValidatorFactory()
				.getValidator();

		assertThat(validator.validate(properties(
				Duration.ZERO,
				Duration.ofHours(1),
				100
		))).anyMatch(violation -> violation.getMessage().equals(
				"lunch chat retention must be positive"
		));
		var cleanupViolations = validator.validate(properties(
				Duration.ofDays(7),
				Duration.ZERO,
				0
		));
		assertThat(cleanupViolations).anyMatch(violation ->
				violation.getMessage().equals(
						"lunch chat cleanup fixed delay must be positive"
				)
		);
		assertThat(cleanupViolations).anyMatch(violation ->
				violation.getPropertyPath().toString().equals(
						"chat.cleanup.batchSize"
				)
		);
	}

	private LunchProperties properties(
			Duration retention,
			Duration cleanupDelay,
			int cleanupBatchSize
	) {
		return new LunchProperties(
				ZONE_ID,
				LocalTime.of(12, 0),
				LocalTime.of(15, 0),
				Duration.ofMinutes(15),
				Duration.ofMinutes(10),
				2,
				Duration.ofMinutes(5),
				Duration.ofHours(1),
				new LunchProperties.Matching(
						false,
						Duration.ofMinutes(1),
						50
				),
				new LunchProperties.Lifecycle(
						false,
						Duration.ofMinutes(1),
						100
				),
				new LunchProperties.Chat(
						retention,
						new LunchProperties.Cleanup(
								false,
								cleanupDelay,
								cleanupBatchSize
						)
				)
		);
	}
}
