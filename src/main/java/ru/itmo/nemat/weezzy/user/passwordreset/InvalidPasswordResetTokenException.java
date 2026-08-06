package ru.itmo.nemat.weezzy.user.passwordreset;

import ru.itmo.nemat.weezzy.common.exception.UnauthorizedException;

public class InvalidPasswordResetTokenException extends UnauthorizedException {
	private static final String MESSAGE =
			"Password reset token is invalid or expired";

	public InvalidPasswordResetTokenException() {
		super(MESSAGE);
	}
}
