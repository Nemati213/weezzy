package ru.itmo.nemat.weezzy.profile.photo;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class ProfilePhotoNotReadyException extends ConflictException {
	public ProfilePhotoNotReadyException(UUID photoId) {
		super("Profile photo is not ready: " + photoId);
	}
}
