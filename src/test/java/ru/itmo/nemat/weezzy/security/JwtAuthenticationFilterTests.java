package ru.itmo.nemat.weezzy.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.itmo.nemat.weezzy.security.revocation.AccessTokenRevocation;
import ru.itmo.nemat.weezzy.security.revocation.AccessTokenRevocationReason;
import ru.itmo.nemat.weezzy.security.revocation.AccessTokenRevocationService;
import ru.itmo.nemat.weezzy.user.AccountAccessService;
import ru.itmo.nemat.weezzy.user.UserRepository;
import ru.itmo.nemat.weezzy.user.UserRole;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTests {
	private static final String TOKEN = "signed.jwt.token";

	@Mock
	private JwtService jwtService;
	@Mock
	private UserRepository userRepository;
	@Mock
	private AccountAccessService accountAccessService;
	@Mock
	private AccessTokenRevocationService revocationService;
	@Mock
	private FilterChain filterChain;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void authenticatesWithoutDatabaseRequestWhenRedisHasNoRevocation() throws Exception {
		JwtAuthenticatedUser user = authenticatedUser();
		MockHttpServletRequest request = requestWithToken();
		when(jwtService.parseToken(TOKEN)).thenReturn(Optional.of(user));
		when(revocationService.isEnabled()).thenReturn(true);
		when(revocationService.findRevocation(
				user.id(),
				user.issuedAtEpochMilli()
		)).thenReturn(Optional.empty());

		filter().doFilter(request, new MockHttpServletResponse(), filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()
				.getPrincipal()).isEqualTo(user);
		verify(userRepository, never()).existsById(user.id());
		verify(accountAccessService, never()).ensureAccessAllowed(user.id());
	}

	@Test
	void marksSanctionedTokenAsRevoked() throws Exception {
		JwtAuthenticatedUser user = authenticatedUser();
		MockHttpServletRequest request = requestWithToken();
		AccessTokenRevocation revocation = new AccessTokenRevocation(
				user.issuedAtEpochMilli(),
				AccessTokenRevocationReason.ACCOUNT_SANCTION
		);
		when(jwtService.parseToken(TOKEN)).thenReturn(Optional.of(user));
		when(revocationService.isEnabled()).thenReturn(true);
		when(revocationService.findRevocation(
				user.id(),
				user.issuedAtEpochMilli()
		)).thenReturn(Optional.of(revocation));

		filter().doFilter(request, new MockHttpServletResponse(), filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		assertThat(request.getAttribute(JwtAuthenticationFilter.REVOCATION_ATTRIBUTE))
				.isEqualTo(revocation);
	}

	@Test
	void failsClosedWhenRedisIsUnavailable() throws Exception {
		JwtAuthenticatedUser user = authenticatedUser();
		MockHttpServletRequest request = requestWithToken();
		when(jwtService.parseToken(TOKEN)).thenReturn(Optional.of(user));
		when(revocationService.isEnabled()).thenReturn(true);
		when(revocationService.findRevocation(
				user.id(),
				user.issuedAtEpochMilli()
		)).thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

		filter().doFilter(request, new MockHttpServletResponse(), filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		assertThat(request.getAttribute(
				JwtAuthenticationFilter.REDIS_UNAVAILABLE_ATTRIBUTE
		)).isEqualTo(true);
	}

	private JwtAuthenticationFilter filter() {
		return new JwtAuthenticationFilter(
				jwtService,
				userRepository,
				accountAccessService,
				revocationService
		);
	}

	private MockHttpServletRequest requestWithToken() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
		return request;
	}

	private JwtAuthenticatedUser authenticatedUser() {
		return new JwtAuthenticatedUser(
				UUID.randomUUID(),
				"jwt-filter@itmo.ru",
				UserRole.USER,
				System.currentTimeMillis()
		);
	}
}
