package ru.itmo.nemat.weezzy.user.emailverification;

import ru.itmo.nemat.weezzy.common.exception.UnauthorizedException;

public class InvalidEmailVerificationTokenException extends UnauthorizedException {
	private static final String MESSAGE =
			"Email verification token is invalid or expired";

	public InvalidEmailVerificationTokenException() {
		super(MESSAGE);
	}
}
