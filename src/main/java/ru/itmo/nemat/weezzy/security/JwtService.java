package ru.itmo.nemat.weezzy.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserRole;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {
	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final byte[] secret;
	private final Duration accessTokenTtl;

	public JwtService(
			ObjectMapper objectMapper,
			@Value("${app.security.jwt.secret}") String secret,
			@Value("${app.security.jwt.access-token-ttl}") Duration accessTokenTtl
	) {
		if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException("JWT secret must be at least 32 bytes long");
		}
		this.objectMapper = objectMapper;
		this.clock = Clock.systemUTC();
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
		this.accessTokenTtl = accessTokenTtl;
	}

	public String generateAccessToken(User user) {
		Instant issuedAt = Instant.now(clock);
		Instant expiresAt = issuedAt.plus(accessTokenTtl);
		Map<String, Object> header = Map.of(
				"alg", "HS256",
				"typ", "JWT"
		);
		Map<String, Object> payload = Map.of(
				"sub", user.getEmail(),
				"userId", user.getId().toString(),
				"role", user.getRole().name(),
				"iat", issuedAt.getEpochSecond(),
				"exp", expiresAt.getEpochSecond()
		);

		String encodedHeader = encodeJson(header);
		String encodedPayload = encodeJson(payload);
		String signingInput = encodedHeader + "." + encodedPayload;

		return signingInput + "." + sign(signingInput);
	}

	public Optional<JwtAuthenticatedUser> parseToken(String token) {
		String[] parts = token.split("\\.");
		if (parts.length != 3 || !isSignatureValid(parts)) {
			return Optional.empty();
		}

		try {
			JsonNode payload = objectMapper.readTree(base64UrlDecode(parts[1]));
			if (payload.path("exp").asLong() <= Instant.now(clock).getEpochSecond()) {
				return Optional.empty();
			}

			return Optional.of(new JwtAuthenticatedUser(
					UUID.fromString(payload.path("userId").asText()),
					payload.path("sub").asText(),
					UserRole.valueOf(payload.path("role").asText())
			));
		} catch (RuntimeException exception) {
			return Optional.empty();
		} catch (Exception exception) {
			return Optional.empty();
		}
	}

	private boolean isSignatureValid(String[] tokenParts) {
		String signingInput = tokenParts[0] + "." + tokenParts[1];
		String expectedSignature = sign(signingInput);

		return MessageDigest.isEqual(
				expectedSignature.getBytes(StandardCharsets.UTF_8),
				tokenParts[2].getBytes(StandardCharsets.UTF_8)
		);
	}

	private String encodeJson(Map<String, Object> value) {
		try {
			return base64UrlEncode(objectMapper.writeValueAsBytes(value));
		} catch (Exception exception) {
			throw new IllegalStateException("Could not create JWT", exception);
		}
	}

	private String sign(String signingInput) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			return base64UrlEncode(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Could not sign JWT", exception);
		}
	}

	private String base64UrlEncode(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	private byte[] base64UrlDecode(String value) {
		return Base64.getUrlDecoder().decode(value);
	}
}
