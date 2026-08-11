package ru.itmo.nemat.weezzy.moderation.report;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class ProfileReportDecisionRequiredException extends BadRequestException {
	public ProfileReportDecisionRequiredException() {
		super("Moderator decision is required when closing a profile report");
	}
}
