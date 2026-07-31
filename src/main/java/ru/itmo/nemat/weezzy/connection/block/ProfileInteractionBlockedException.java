package ru.itmo.nemat.weezzy.connection.block;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class ProfileInteractionBlockedException extends ConflictException {
	public ProfileInteractionBlockedException(UUID firstProfileId, UUID secondProfileId) {
		super("Profiles cannot interact while a block exists: "
				+ firstProfileId + " and " + secondProfileId);
	}
}
