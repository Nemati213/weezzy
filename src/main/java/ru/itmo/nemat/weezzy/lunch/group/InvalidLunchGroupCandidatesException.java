package ru.itmo.nemat.weezzy.lunch.group;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidLunchGroupCandidatesException extends BadRequestException {
	public InvalidLunchGroupCandidatesException(String reason) {
		super("Invalid lunch group candidates: " + reason);
	}
}
