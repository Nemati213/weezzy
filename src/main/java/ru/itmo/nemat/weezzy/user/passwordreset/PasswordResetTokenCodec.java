package ru.itmo.nemat.weezzy.user.passwordreset;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Component
public class PasswordResetTokenCodec {
	private static final int SECRET_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	public GeneratedPasswordResetToken generate() {
		UUID tokenId = UUID.randomUUID();
		byte[] secretBytes = new byte[SECRET_BYTES];
		secureRandom.nextBytes(secretBytes);
		String secret = Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(secretBytes);

		return new GeneratedPasswordResetToken(
				tokenId,
				tokenId + "." + secret,
				hash(secret)
		);
	}

	public Optional<ParsedPasswordResetToken> parse(String rawToken) {
		if (rawToken == null) {
			return Optional.empty();
		}

		int separator = rawToken.indexOf('.');
		if (separator <= 0 || separator == rawToken.length() - 1
				|| rawToken.indexOf('.', separator + 1) >= 0) {
			return Optional.empty();
		}

		try {
			UUID tokenId = UUID.fromString(rawToken.substring(0, separator));
			String secret = rawToken.substring(separator + 1);
			return Optional.of(new ParsedPasswordResetToken(
					tokenId,
					hash(secret)
			));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	public boolean matches(String expectedHash, String actualHash) {
		return MessageDigest.isEqual(
				expectedHash.getBytes(StandardCharsets.US_ASCII),
				actualHash.getBytes(StandardCharsets.US_ASCII)
		);
	}

	private String hash(String secret) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(
					digest.digest(secret.getBytes(StandardCharsets.UTF_8))
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	public record GeneratedPasswordResetToken(
			UUID id,
			String value,
			String secretHash
	) {
	}

	public record ParsedPasswordResetToken(
			UUID id,
			String secretHash
	) {
	}
}
