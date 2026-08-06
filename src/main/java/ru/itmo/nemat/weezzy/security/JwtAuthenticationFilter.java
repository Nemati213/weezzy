package ru.itmo.nemat.weezzy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.itmo.nemat.weezzy.user.UserRepository;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final UserRepository userRepository;

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
			jwtService.parseToken(token)
					.filter(user -> userRepository.existsById(user.id()))
					.ifPresent(user -> authenticate(request, user));
		}

		filterChain.doFilter(request, response);
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
