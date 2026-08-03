package ru.itmo.nemat.weezzy.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.common.error.ApiErrorResponse;
import ru.itmo.nemat.weezzy.common.observability.RequestCorrelationFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {
	private final ObjectMapper objectMapper;

	public void write(
			HttpServletRequest request,
			HttpServletResponse response,
			HttpStatus status,
			String message
	) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
				LocalDateTime.now(),
				status.value(),
				status.getReasonPhrase(),
				message,
				request.getRequestURI(),
				RequestCorrelationFilter.getRequestId(request)
		));
	}
}
