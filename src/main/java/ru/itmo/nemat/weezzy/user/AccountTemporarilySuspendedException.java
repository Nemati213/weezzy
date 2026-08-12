package ru.itmo.nemat.weezzy.user;

import ru.itmo.nemat.weezzy.common.exception.ForbiddenException;

import java.time.LocalDateTime;

public class AccountTemporarilySuspendedException extends ForbiddenException {
	public AccountTemporarilySuspendedException(
			String reason,
			LocalDateTime expiresAt
	) {
		super("Account is temporarily suspended until " + expiresAt + ": " + reason);
	}
}
