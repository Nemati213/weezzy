package ru.itmo.nemat.weezzy.moderation.report;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidProfileReportStatusException extends BadRequestException {
	public InvalidProfileReportStatusException(ProfileReportStatus status) {
		super("Final profile report status must be REJECTED or RESOLVED: " + status);
	}
}
