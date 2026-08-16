package ru.itmo.nemat.weezzy.lunch.request;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class ActiveLunchRequestAlreadyExistsException extends ConflictException {
	public ActiveLunchRequestAlreadyExistsException(UUID profileId) {
		super("Active lunch request already exists for profile: " + profileId);
	}
}
