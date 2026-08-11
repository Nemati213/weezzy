package ru.itmo.nemat.weezzy.moderation.report;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class ProfileReportStatusConflictException extends ConflictException {
	public ProfileReportStatusConflictException(
			UUID reportId,
			ProfileReportStatus currentStatus,
			String requestedAction
	) {
		super("Profile report %s cannot be %s while its status is %s"
				.formatted(reportId, requestedAction, currentStatus));
	}
}
