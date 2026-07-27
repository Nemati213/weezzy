package ru.itmo.nemat.weezzy.user;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class UserNotFoundException extends NotFoundException {
	public UserNotFoundException(UUID id) {
		super("User not found: " + id);
	}

	public UserNotFoundException(String email) {
		super("User not found: " + email);
	}
}
