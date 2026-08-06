package ru.itmo.nemat.weezzy.user.emailverification;

import ru.itmo.nemat.weezzy.common.exception.ForbiddenException;

public class EmailNotVerifiedException extends ForbiddenException {
	public EmailNotVerifiedException() {
		super("Email is not verified");
	}
}
