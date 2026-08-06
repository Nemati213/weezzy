package ru.itmo.nemat.weezzy.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.itmo.nemat.weezzy.common.exception.BadRequestException;
import ru.itmo.nemat.weezzy.common.exception.ConflictException;
import ru.itmo.nemat.weezzy.common.exception.ForbiddenException;
import ru.itmo.nemat.weezzy.common.exception.NotFoundException;
import ru.itmo.nemat.weezzy.common.exception.UnauthorizedException;
import ru.itmo.nemat.weezzy.common.observability.RequestCorrelationFilter;

import java.time.LocalDateTime;

@RestControllerAdvice
public class ApiExceptionHandler {
	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ApiErrorResponse> handleForbidden(
			ForbiddenException exception,
			HttpServletRequest request
	) {
		return buildResponse(HttpStatus.FORBIDDEN, exception.getMessage(), request);
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<ApiErrorResponse> handleUnauthorized(
			UnauthorizedException exception,
			HttpServletRequest request
	) {
		return buildResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException exception, HttpServletRequest request) {
		return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request);
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException exception, HttpServletRequest request) {
		return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request);
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException exception, HttpServletRequest request) {
		return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.orElse("Request validation failed");

		return buildResponse(HttpStatus.BAD_REQUEST, message, request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
			ConstraintViolationException exception,
			HttpServletRequest request
	) {
		String message = exception.getConstraintViolations().stream()
				.findFirst()
				.map(violation -> violation.getPropertyPath() + ": "
						+ violation.getMessage())
				.orElse("Request validation failed");

		return buildResponse(HttpStatus.BAD_REQUEST, message, request);
	}

	private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String message, HttpServletRequest request) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(
				LocalDateTime.now(),
				status.value(),
				status.getReasonPhrase(),
				message,
				request.getRequestURI(),
				RequestCorrelationFilter.getRequestId(request)
		));
	}
}
