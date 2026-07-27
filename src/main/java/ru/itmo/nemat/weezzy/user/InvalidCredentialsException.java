package ru.itmo.nemat.weezzy.user;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidCredentialsException extends BadRequestException {
	public InvalidCredentialsException() {
		super("Invalid email or password");
	}
}
