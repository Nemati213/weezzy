package ru.itmo.nemat.weezzy.moderation.sanction;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

import java.util.UUID;

public class AccountSanctionReportMismatchException extends BadRequestException {
	public AccountSanctionReportMismatchException(UUID reportId, UUID targetUserId) {
		super("Profile report %s does not target user %s"
				.formatted(reportId, targetUserId));
	}
}
