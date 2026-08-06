package ru.itmo.nemat.weezzy.user.emailverification;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class EmailAlreadyVerifiedException extends BadRequestException {
	public EmailAlreadyVerifiedException() {
		super("Email is already verified");
	}
}
