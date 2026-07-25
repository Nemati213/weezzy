package ru.itmo.nemat.weezzy.goal;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class DuplicateGoalException extends ConflictException {
	public DuplicateGoalException(String value) {
		super("Goal already exists: " + value);
	}
}
