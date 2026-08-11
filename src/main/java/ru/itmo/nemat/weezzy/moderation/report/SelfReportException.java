package ru.itmo.nemat.weezzy.moderation.report;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

import java.util.UUID;

public class SelfReportException extends BadRequestException {
	public SelfReportException(UUID profileId) {
		super("A profile cannot report itself: " + profileId);
	}
}
