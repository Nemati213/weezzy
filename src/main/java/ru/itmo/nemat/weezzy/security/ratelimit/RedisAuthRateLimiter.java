package ru.itmo.nemat.weezzy.security.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisAuthRateLimiter {
	private static final String KEY_PREFIX = "weezzy:rate-limit:auth:";
	private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
			new DefaultRedisScript<>("""
					local current = redis.call('INCR', KEYS[1])
					if current == 1 then
					    redis.call('PEXPIRE', KEYS[1], ARGV[1])
					end
					return current
					""", Long.class);

	private final StringRedisTemplate redisTemplate;

	public AuthRateLimitDecision consume(
			String operation,
			String clientAddress,
			AuthRateLimitProperties.Policy policy
	) {
		String key = KEY_PREFIX + operation + ":" + clientAddress;
		Long count = redisTemplate.execute(
				INCREMENT_SCRIPT,
				List.of(key),
				Long.toString(policy.window().toMillis())
		);
		if (count == null) {
			throw new DataRetrievalFailureException(
					"Redis rate limit script returned no result"
			);
		}

		int remaining = (int) Math.max(0, policy.capacity() - count);
		return new AuthRateLimitDecision(
				count <= policy.capacity(),
				policy.capacity(),
				remaining,
				resolveRetryAfter(key, policy.window())
		);
	}

	private Duration resolveRetryAfter(String key, Duration fallback) {
		Long remainingMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
		if (remainingMillis == null || remainingMillis <= 0) {
			return fallback;
		}

		return Duration.ofMillis(remainingMillis);
	}
}
