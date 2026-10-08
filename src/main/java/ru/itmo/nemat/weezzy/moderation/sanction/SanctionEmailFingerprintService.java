package ru.itmo.nemat.weezzy.moderation.sanction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.itmo.nemat.weezzy.user.EmailNormalizer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Service
public class SanctionEmailFingerprintService {
	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final byte[] secret;

	public SanctionEmailFingerprintService(
			@Value("${app.security.sanction-email-hmac-key}") String secret
	) {
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
		if (this.secret.length < 32) {
			throw new IllegalArgumentException(
					"Sanction email HMAC key must be at least 32 bytes long"
			);
		}
	}

	public String fingerprint(String email) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			byte[] normalizedEmail = EmailNormalizer.normalize(email)
					.getBytes(StandardCharsets.UTF_8);
			return HexFormat.of().formatHex(mac.doFinal(normalizedEmail));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException(
					"Could not calculate sanction email fingerprint",
					exception
			);
		}
	}
}
