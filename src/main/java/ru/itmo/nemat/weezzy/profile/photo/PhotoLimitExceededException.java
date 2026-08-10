package ru.itmo.nemat.weezzy.profile.photo;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class PhotoLimitExceededException extends ConflictException {
	public PhotoLimitExceededException(UUID profileId, int maxPhotos) {
		super("Profile " + profileId
				+ " already has the maximum of " + maxPhotos + " photos");
	}
}
