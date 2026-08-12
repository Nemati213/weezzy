package ru.itmo.nemat.weezzy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.itmo.nemat.weezzy.common.exception.ForbiddenException;
import ru.itmo.nemat.weezzy.security.revocation.AccessTokenRevocation;
import ru.itmo.nemat.weezzy.security.revocation.AccessTokenRevocationReason;
import ru.itmo.nemat.weezzy.security.revocation.AccessTokenRevocationService;
import ru.itmo.nemat.weezzy.user.AccountAccessService;
import ru.itmo.nemat.weezzy.user.UserRepository;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private static final String BEARER_PREFIX = "Bearer ";
	public static final String REVOCATION_ATTRIBUTE =
			JwtAuthenticationFilter.class.getName() + ".revocation";
	public static final String REDIS_UNAVAILABLE_ATTRIBUTE =
			JwtAuthenticationFilter.class.getName() + ".redisUnavailable";

	private final JwtService jwtService;
	private final UserRepository userRepository;
	private final AccountAccessService accountAccessService;
	private final AccessTokenRevocationService revocationService;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (authorizationHeader != null
				&& authorizationHeader.startsWith(BEARER_PREFIX)
				&& SecurityContextHolder.getContext().getAuthentication() == null) {
			String token = authorizationHeader.substring(BEARER_PREFIX.length());
			Optional<JwtAuthenticatedUser> parsedUser = jwtService.parseToken(token);
			if (parsedUser.isPresent()) {
				processToken(request, parsedUser.get());
			}
		}

		filterChain.doFilter(request, response);
	}

	private void processToken(HttpServletRequest request, JwtAuthenticatedUser user) {
		if (revocationService.isEnabled()) {
			try {
				Optional<AccessTokenRevocation> revocation =
						revocationService.findRevocation(
								user.id(),
								user.issuedAtEpochMilli()
						);
				if (revocation.isPresent()) {
					request.setAttribute(REVOCATION_ATTRIBUTE, revocation.get());
					return;
				}
			} catch (DataAccessException exception) {
				request.setAttribute(REDIS_UNAVAILABLE_ATTRIBUTE, true);
				return;
			}

			authenticate(request, user);
			return;
		}

		if (!userRepository.existsById(user.id())) {
			return;
		}
		try {
			accountAccessService.ensureAccessAllowed(user.id());
		} catch (ForbiddenException exception) {
			request.setAttribute(
					REVOCATION_ATTRIBUTE,
					new AccessTokenRevocation(
							Long.MAX_VALUE,
							AccessTokenRevocationReason.ACCOUNT_SANCTION
					)
			);
			return;
		}
		authenticate(request, user);
	}

	private void authenticate(
			HttpServletRequest request,
			JwtAuthenticatedUser user
	) {
		List<SimpleGrantedAuthority> authorities = List.of(
				new SimpleGrantedAuthority("ROLE_" + user.role().name())
		);
		UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(user, null, authorities);
		authentication.setDetails(
				new WebAuthenticationDetailsSource().buildDetails(request)
		);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
