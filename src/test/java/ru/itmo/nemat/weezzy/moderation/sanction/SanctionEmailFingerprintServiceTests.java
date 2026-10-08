package ru.itmo.nemat.weezzy.moderation.sanction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SanctionEmailFingerprintServiceTests {
	private static final String KEY =
			"test-sanction-email-key-with-at-least-32-bytes";

	@Test
	void fingerprintUsesNormalizedEmail() {
		SanctionEmailFingerprintService service =
				new SanctionEmailFingerprintService(KEY);

		String fingerprint = service.fingerprint("  Student@ITMO.RU  ");

		assertThat(fingerprint).isEqualTo(service.fingerprint("student@itmo.ru"));
		assertThat(fingerprint).matches("[0-9a-f]{64}");
		assertThat(fingerprint).doesNotContain("student");
	}

	@Test
	void fingerprintChangesWithEmailAndKey() {
		SanctionEmailFingerprintService service =
				new SanctionEmailFingerprintService(KEY);
		SanctionEmailFingerprintService otherKeyService =
				new SanctionEmailFingerprintService(KEY + "-other");

		String fingerprint = service.fingerprint("student@itmo.ru");

		assertThat(fingerprint).isNotEqualTo(service.fingerprint("other@itmo.ru"));
		assertThat(fingerprint)
				.isNotEqualTo(otherKeyService.fingerprint("student@itmo.ru"));
	}

	@Test
	void rejectsShortKey() {
		assertThatThrownBy(() -> new SanctionEmailFingerprintService("short"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
