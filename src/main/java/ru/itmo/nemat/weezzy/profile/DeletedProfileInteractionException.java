package ru.itmo.nemat.weezzy.profile;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class DeletedProfileInteractionException extends ConflictException {
	public DeletedProfileInteractionException(UUID profileId) {
		super("Cannot interact with deleted profile: " + profileId);
	}
}
