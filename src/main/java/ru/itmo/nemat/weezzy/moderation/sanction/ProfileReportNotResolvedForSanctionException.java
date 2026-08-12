package ru.itmo.nemat.weezzy.moderation.sanction;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class ProfileReportNotResolvedForSanctionException extends ConflictException {
	public ProfileReportNotResolvedForSanctionException(UUID reportId) {
		super("Profile report must be RESOLVED before creating a sanction: " + reportId);
	}
}
