package ru.itmo.nemat.weezzy.moderation.report;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class DuplicateOpenProfileReportException extends ConflictException {
	public DuplicateOpenProfileReportException(
			UUID reporterProfileId,
			UUID targetProfileId
	) {
		super("An open report from profile %s against profile %s already exists"
				.formatted(reporterProfileId, targetProfileId));
	}
}
