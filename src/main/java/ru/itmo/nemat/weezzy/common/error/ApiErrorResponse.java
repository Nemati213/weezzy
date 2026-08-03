package ru.itmo.nemat.weezzy.common.error;

import java.time.LocalDateTime;

public record ApiErrorResponse(
		LocalDateTime timestamp,
		int status,
		String error,
		String message,
		String path,
		String requestId
) {
}
