package ru.itmo.nemat.weezzy.user;

import ru.itmo.nemat.weezzy.common.exception.ForbiddenException;

public class RegistrationRestrictedException extends ForbiddenException {
	public RegistrationRestrictedException() {
		super("Registration is not allowed");
	}
}
