package ru.itmo.nemat.weezzy.moderation.report;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class ProfileReportCommentRequiredException extends BadRequestException {
	public ProfileReportCommentRequiredException() {
		super("Comment is required when report reason is OTHER");
	}
}
