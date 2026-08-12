package ru.itmo.nemat.weezzy.moderation.sanction;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidAccountSanctionExpirationException extends BadRequestException {
	public InvalidAccountSanctionExpirationException(String message) {
		super(message);
	}
}
