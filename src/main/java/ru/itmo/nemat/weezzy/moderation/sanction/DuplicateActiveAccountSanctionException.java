package ru.itmo.nemat.weezzy.moderation.sanction;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class DuplicateActiveAccountSanctionException extends ConflictException {
	public DuplicateActiveAccountSanctionException(UUID targetUserId) {
		super("User already has an active account sanction: " + targetUserId);
	}
}
