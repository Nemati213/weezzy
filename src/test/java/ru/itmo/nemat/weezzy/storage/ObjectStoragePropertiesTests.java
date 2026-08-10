package ru.itmo.nemat.weezzy.storage;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStoragePropertiesTests {
	private final Validator validator = Validation
			.buildDefaultValidatorFactory()
			.getValidator();

	@Test
	void acceptsPositiveOrderedTimeouts() {
		assertThat(validator.validate(properties(
				Duration.ofSeconds(7),
				Duration.ofSeconds(15)
		))).isEmpty();
	}

	@Test
	void rejectsAttemptTimeoutNotShorterThanTotalTimeout() {
		assertThat(validator.validate(properties(
				Duration.ofSeconds(15),
				Duration.ofSeconds(15)
		))).isNotEmpty();
	}

	@Test
	void rejectsNonPositiveNetworkTimeout() {
		ObjectStorageProperties properties = new ObjectStorageProperties(
				URI.create("http://localhost:9000"),
				"us-east-1",
				"access-key",
				"secret-key",
				"bucket",
				Duration.ofMinutes(15),
				Duration.ofHours(1),
				Duration.ZERO,
				Duration.ofSeconds(5),
				Duration.ofSeconds(7),
				Duration.ofSeconds(15)
		);

		assertThat(validator.validate(properties)).isNotEmpty();
	}

	private ObjectStorageProperties properties(
			Duration attemptTimeout,
			Duration totalTimeout
	) {
		return new ObjectStorageProperties(
				URI.create("http://localhost:9000"),
				"us-east-1",
				"access-key",
				"secret-key",
				"bucket",
				Duration.ofMinutes(15),
				Duration.ofHours(1),
				Duration.ofSeconds(2),
				Duration.ofSeconds(5),
				attemptTimeout,
				totalTimeout
		);
	}
}
