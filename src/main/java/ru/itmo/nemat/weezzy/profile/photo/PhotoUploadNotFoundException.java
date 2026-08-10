package ru.itmo.nemat.weezzy.profile.photo;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class PhotoUploadNotFoundException extends ConflictException {
	public PhotoUploadNotFoundException(UUID photoId) {
		super("Uploaded object was not found for profile photo: " + photoId);
	}
}
