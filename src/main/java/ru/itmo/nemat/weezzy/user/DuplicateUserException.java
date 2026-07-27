package ru.itmo.nemat.weezzy.user;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class DuplicateUserException extends ConflictException {
	public DuplicateUserException(String email) {
		super("User already exists: " + email);
	}
}
