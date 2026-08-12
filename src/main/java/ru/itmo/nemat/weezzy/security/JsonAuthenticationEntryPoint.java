package ru.itmo.nemat.weezzy.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.security.revocation.AccessTokenRevocation;
import ru.itmo.nemat.weezzy.security.revocation.AccessTokenRevocationReason;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
	private final SecurityErrorResponseWriter errorResponseWriter;

	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException exception
	) throws IOException, ServletException {
		if (Boolean.TRUE.equals(request.getAttribute(
				JwtAuthenticationFilter.REDIS_UNAVAILABLE_ATTRIBUTE
		))) {
			errorResponseWriter.write(
					request,
					response,
					HttpStatus.SERVICE_UNAVAILABLE,
					"Authentication service is temporarily unavailable"
			);
			return;
		}

		AccessTokenRevocation revocation = (AccessTokenRevocation) request.getAttribute(
				JwtAuthenticationFilter.REVOCATION_ATTRIBUTE
		);
		if (revocation != null) {
			boolean sanction = revocation.reason()
					== AccessTokenRevocationReason.ACCOUNT_SANCTION;
			errorResponseWriter.write(
					request,
					response,
					sanction ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED,
					sanction
							? "Account access is restricted by a sanction"
							: "Access token has been revoked"
			);
			return;
		}

		errorResponseWriter.write(
				request,
				response,
				HttpStatus.UNAUTHORIZED,
				"Authentication is required"
		);
	}
}
