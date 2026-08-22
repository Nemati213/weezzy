package ru.itmo.nemat.weezzy.lunch.group;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class LunchGroupFormationConflictException extends ConflictException {
	public LunchGroupFormationConflictException(String reason) {
		super("Lunch group cannot be formed: " + reason);
	}
}
