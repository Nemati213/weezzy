package ru.itmo.nemat.weezzy.moderation.sanction;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class AccountSanctionStatusConflictException extends ConflictException {
	public AccountSanctionStatusConflictException(
			UUID sanctionId,
			AccountSanctionStatus currentStatus
	) {
		super("Account sanction %s cannot be revoked while its status is %s"
				.formatted(sanctionId, currentStatus));
	}
}
