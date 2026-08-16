package ru.itmo.nemat.weezzy.lunch.request;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class LunchProfileNotActiveException extends ConflictException {
	public LunchProfileNotActiveException(UUID profileId) {
		super("Profile must be active to create a lunch request: " + profileId);
	}
}
