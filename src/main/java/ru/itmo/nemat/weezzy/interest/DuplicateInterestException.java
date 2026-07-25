package ru.itmo.nemat.weezzy.interest;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class DuplicateInterestException extends ConflictException {
	public DuplicateInterestException(String name) {
		super("Interest already exists: " + name);
	}
}
