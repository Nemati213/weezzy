package ru.itmo.nemat.weezzy.security.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.itmo.nemat.weezzy.security.SecurityErrorResponseWriter;

import java.io.IOException;
import java.time.Duration;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {
	private static final String LOGIN_PATH = "/api/auth/login";
	private static final String REGISTER_PATH = "/api/auth/register";
	private static final String RATE_LIMIT_HEADER = "X-RateLimit-Limit";
	private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
	private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

	private final AuthRateLimitProperties properties;
	private final RedisAuthRateLimiter rateLimiter;
	private final SecurityErrorResponseWriter errorResponseWriter;
	private final MeterRegistry meterRegistry;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		if (!properties.enabled() || !HttpMethod.POST.matches(request.getMethod())) {
			return true;
		}

		String path = resolveRequestPath(request);
		return !LOGIN_PATH.equals(path) && !REGISTER_PATH.equals(path);
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String operation = resolveOperation(resolveRequestPath(request));
		AuthRateLimitProperties.Policy policy = resolvePolicy(operation);

		try {
			AuthRateLimitDecision decision = rateLimiter.consume(
					operation,
					resolveClientAddress(request),
					policy
			);
			writeRateLimitHeaders(response, decision);

			if (!decision.allowed()) {
				recordMetric(operation, "blocked");
				errorResponseWriter.write(
						request,
						response,
						HttpStatus.TOO_MANY_REQUESTS,
						"Too many authentication attempts"
				);
				return;
			}

			recordMetric(operation, "allowed");
			filterChain.doFilter(request, response);
		} catch (DataAccessException exception) {
			log.error("Authentication rate limiter is unavailable", exception);
			recordMetric(operation, "error");
			errorResponseWriter.write(
					request,
					response,
					HttpStatus.SERVICE_UNAVAILABLE,
					"Authentication service is temporarily unavailable"
			);
		}
	}

	private String resolveOperation(String path) {
		return LOGIN_PATH.equals(path) ? "login" : "register";
	}

	private String resolveRequestPath(HttpServletRequest request) {
		String requestUri = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath == null || contextPath.isEmpty()) {
			return requestUri;
		}

		return requestUri.substring(contextPath.length());
	}

	private AuthRateLimitProperties.Policy resolvePolicy(String operation) {
		return "login".equals(operation) ? properties.login() : properties.register();
	}

	private String resolveClientAddress(HttpServletRequest request) {
		String remoteAddress = request.getRemoteAddr();
		return remoteAddress == null || remoteAddress.isBlank()
				? "unknown"
				: remoteAddress;
	}

	private void writeRateLimitHeaders(
			HttpServletResponse response,
			AuthRateLimitDecision decision
	) {
		response.setHeader(RATE_LIMIT_HEADER, Integer.toString(decision.limit()));
		response.setHeader(
				RATE_LIMIT_REMAINING_HEADER,
				Integer.toString(decision.remaining())
		);
		if (!decision.allowed()) {
			response.setHeader(
					HttpHeaders.RETRY_AFTER,
					Long.toString(toRetryAfterSeconds(decision.retryAfter()))
			);
		}
	}

	private long toRetryAfterSeconds(Duration retryAfter) {
		return Math.max(1, (retryAfter.toMillis() + 999) / 1_000);
	}

	private void recordMetric(String operation, String outcome) {
		meterRegistry.counter(
				"auth.rate.limit.requests",
				"operation",
				operation,
				"outcome",
				outcome
		).increment();
	}
}
