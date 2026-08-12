package ru.itmo.nemat.weezzy.security.revocation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccessTokenRevocationService {
	private static final String KEY_PREFIX = "weezzy:auth:access-revoked-before:";
	private static final Duration EXPIRATION_MARGIN = Duration.ofMinutes(1);

	private final StringRedisTemplate redisTemplate;
	private final boolean enabled;
	private final Duration keyTtl;

	public AccessTokenRevocationService(
			StringRedisTemplate redisTemplate,
			@Value("${app.security.access-token-revocation.enabled:true}")
			boolean enabled,
			@Value("${app.security.jwt.access-token-ttl:PT15M}")
			Duration accessTokenTtl
	) {
		this.redisTemplate = redisTemplate;
		this.enabled = enabled;
		this.keyTtl = accessTokenTtl.plus(EXPIRATION_MARGIN);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void revokeAllIssuedTokens(
			UUID userId,
			AccessTokenRevocationReason reason
	) {
		if (!enabled) {
			return;
		}

		long cutoff = Instant.now().toEpochMilli();
		String value = reason.name() + ":" + cutoff;
		redisTemplate.opsForValue().set(key(userId), value, keyTtl);
	}

	public Optional<AccessTokenRevocation> findRevocation(
			UUID userId,
			long tokenIssuedAtEpochMilli
	) {
		if (!enabled) {
			return Optional.empty();
		}

		String value = redisTemplate.opsForValue().get(key(userId));
		if (value == null) {
			return Optional.empty();
		}

		AccessTokenRevocation revocation = parse(value);
		if (tokenIssuedAtEpochMilli > revocation.revokedBeforeEpochMilli()) {
			return Optional.empty();
		}
		return Optional.of(revocation);
	}

	private AccessTokenRevocation parse(String value) {
		String[] parts = value.split(":", 2);
		try {
			if (parts.length != 2) {
				throw new IllegalArgumentException("Missing revocation cutoff");
			}
			return new AccessTokenRevocation(
					Long.parseLong(parts[1]),
					AccessTokenRevocationReason.valueOf(parts[0])
			);
		} catch (IllegalArgumentException exception) {
			throw new DataRetrievalFailureException(
					"Invalid access token revocation value in Redis",
					exception
			);
		}
	}

	private String key(UUID userId) {
		return KEY_PREFIX + userId;
	}
}
