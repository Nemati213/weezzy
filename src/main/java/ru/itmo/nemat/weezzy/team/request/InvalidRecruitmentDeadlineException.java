package ru.itmo.nemat.weezzy.team.request;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidRecruitmentDeadlineException extends BadRequestException {
	public InvalidRecruitmentDeadlineException() {
		super("Recruitment deadline must be in the future");
	}
}
