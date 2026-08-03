package ru.itmo.nemat.weezzy.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
	public static final String REQUEST_ID_HEADER = "X-Request-ID";
	public static final String REQUEST_ID_ATTRIBUTE =
			RequestCorrelationFilter.class.getName() + ".requestId";

	private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);
	private static final String REQUEST_ID_MDC_KEY = "requestId";
	private static final String HTTP_METHOD_MDC_KEY = "httpRequestMethod";
	private static final String URL_PATH_MDC_KEY = "urlPath";
	private static final String HTTP_STATUS_MDC_KEY = "httpResponseStatusCode";
	private static final String DURATION_MDC_KEY = "durationMs";
	private static final Pattern VALID_REQUEST_ID =
			Pattern.compile("[A-Za-z0-9._-]{1,128}");

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
		long startedAt = System.nanoTime();

		request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		MDC.put(REQUEST_ID_MDC_KEY, requestId);

		try {
			filterChain.doFilter(request, response);
		} finally {
			logCompletion(request, response, startedAt);
			MDC.remove(REQUEST_ID_MDC_KEY);
			MDC.remove(HTTP_METHOD_MDC_KEY);
			MDC.remove(URL_PATH_MDC_KEY);
			MDC.remove(HTTP_STATUS_MDC_KEY);
			MDC.remove(DURATION_MDC_KEY);
		}
	}

	public static String getRequestId(HttpServletRequest request) {
		Object requestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
		return requestId instanceof String value ? value : null;
	}

	private String resolveRequestId(String providedRequestId) {
		if (providedRequestId != null
				&& VALID_REQUEST_ID.matcher(providedRequestId).matches()) {
			return providedRequestId;
		}

		return UUID.randomUUID().toString();
	}

	private void logCompletion(
			HttpServletRequest request,
			HttpServletResponse response,
			long startedAt
	) {
		MDC.put(HTTP_METHOD_MDC_KEY, request.getMethod());
		MDC.put(URL_PATH_MDC_KEY, request.getRequestURI());
		MDC.put(HTTP_STATUS_MDC_KEY, Integer.toString(response.getStatus()));
		MDC.put(
				DURATION_MDC_KEY,
				Long.toString((System.nanoTime() - startedAt) / 1_000_000)
		);
		log.info("HTTP request completed");
	}
}
