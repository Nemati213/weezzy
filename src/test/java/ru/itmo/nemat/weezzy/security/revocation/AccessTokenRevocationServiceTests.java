package ru.itmo.nemat.weezzy.security.revocation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessTokenRevocationServiceTests {
	private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

	@Mock
	private StringRedisTemplate redisTemplate;
	@Mock
	private ValueOperations<String, String> valueOperations;

	private AccessTokenRevocationService service;

	@BeforeEach
	void setUp() {
		lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		service = new AccessTokenRevocationService(
				redisTemplate,
				true,
				ACCESS_TOKEN_TTL
		);
	}

	@Test
	void revokesTokensIssuedAtOrBeforeCutoff() {
		UUID userId = UUID.randomUUID();
		long beforeRevocation = System.currentTimeMillis() - 1;

		service.revokeAllIssuedTokens(
				userId,
				AccessTokenRevocationReason.ACCOUNT_SANCTION
		);

		verify(valueOperations).set(
				anyString(),
				org.mockito.ArgumentMatchers.matches("ACCOUNT_SANCTION:[0-9]+"),
				eq(Duration.ofMinutes(16))
		);
		when(valueOperations.get(anyString())).thenReturn(
				"ACCOUNT_SANCTION:" + System.currentTimeMillis()
		);

		Optional<AccessTokenRevocation> result = service.findRevocation(
				userId,
				beforeRevocation
		);

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().reason())
				.isEqualTo(AccessTokenRevocationReason.ACCOUNT_SANCTION);
	}

	@Test
	void allowsTokenIssuedAfterCutoff() {
		UUID userId = UUID.randomUUID();
		when(valueOperations.get(anyString())).thenReturn("ACCOUNT_SANCTION:1000");

		assertThat(service.findRevocation(userId, 1001)).isEmpty();
	}

	@Test
	void rejectsMalformedRedisValue() {
		when(valueOperations.get(anyString())).thenReturn("broken");

		assertThatThrownBy(() -> service.findRevocation(UUID.randomUUID(), 1))
				.isInstanceOf(DataRetrievalFailureException.class);
	}

	@Test
	void disabledServiceDoesNotAccessRedis() {
		AccessTokenRevocationService disabledService =
				new AccessTokenRevocationService(
						redisTemplate,
						false,
						ACCESS_TOKEN_TTL
				);

		disabledService.revokeAllIssuedTokens(
				UUID.randomUUID(),
				AccessTokenRevocationReason.ACCOUNT_DELETION
		);
		assertThat(disabledService.findRevocation(UUID.randomUUID(), 1)).isEmpty();

		verify(valueOperations, never()).set(
				anyString(),
				anyString(),
				org.mockito.ArgumentMatchers.any(Duration.class)
		);
	}
}
