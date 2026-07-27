package ru.itmo.nemat.weezzy.profile;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class ProfileAlreadyExistsForUserException extends ConflictException {
	public ProfileAlreadyExistsForUserException(UUID userId) {
		super("Profile already exists for user: " + userId);
	}
}
