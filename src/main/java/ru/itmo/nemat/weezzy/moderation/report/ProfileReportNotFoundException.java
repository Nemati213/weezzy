package ru.itmo.nemat.weezzy.moderation.report;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class ProfileReportNotFoundException extends NotFoundException {
	public ProfileReportNotFoundException(UUID reportId) {
		super("Profile report not found: " + reportId);
	}
}
