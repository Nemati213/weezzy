package ru.itmo.nemat.weezzy.team.request;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidRecruitmentSizeException extends BadRequestException {
	public InvalidRecruitmentSizeException(long expectedSize) {
		super("Target team size must equal current members plus requested slots: "
				+ expectedSize);
	}
}
