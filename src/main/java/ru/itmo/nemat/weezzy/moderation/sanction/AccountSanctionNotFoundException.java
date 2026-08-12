package ru.itmo.nemat.weezzy.moderation.sanction;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class AccountSanctionNotFoundException extends NotFoundException {
	public AccountSanctionNotFoundException(UUID sanctionId) {
		super("Account sanction not found: " + sanctionId);
	}
}
