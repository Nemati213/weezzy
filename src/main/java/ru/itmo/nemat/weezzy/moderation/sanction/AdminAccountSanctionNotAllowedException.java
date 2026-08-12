package ru.itmo.nemat.weezzy.moderation.sanction;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

import java.util.UUID;

public class AdminAccountSanctionNotAllowedException extends BadRequestException {
	public AdminAccountSanctionNotAllowedException(UUID targetUserId) {
		super("Administrator accounts cannot be sanctioned: " + targetUserId);
	}
}
