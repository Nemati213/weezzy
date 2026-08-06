package ru.itmo.nemat.weezzy.security.session;

import ru.itmo.nemat.weezzy.common.exception.UnauthorizedException;

public class InvalidRefreshTokenException extends UnauthorizedException {
	private static final String MESSAGE = "Refresh token is invalid or expired";

	public InvalidRefreshTokenException() {
		super(MESSAGE);
	}
}
