package ru.itmo.nemat.weezzy.user;

import ru.itmo.nemat.weezzy.common.exception.ForbiddenException;

public class AccountPermanentlyBannedException extends ForbiddenException {
	public AccountPermanentlyBannedException(String reason) {
		super("Account is permanently banned: " + reason);
	}
}
