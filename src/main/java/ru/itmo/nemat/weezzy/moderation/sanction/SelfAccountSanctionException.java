package ru.itmo.nemat.weezzy.moderation.sanction;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

import java.util.UUID;

public class SelfAccountSanctionException extends BadRequestException {
	public SelfAccountSanctionException(UUID moderatorUserId) {
		super("An administrator cannot sanction their own account: " + moderatorUserId);
	}
}
