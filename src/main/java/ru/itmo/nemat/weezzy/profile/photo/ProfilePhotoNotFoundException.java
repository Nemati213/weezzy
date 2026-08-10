package ru.itmo.nemat.weezzy.profile.photo;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class ProfilePhotoNotFoundException extends NotFoundException {
	public ProfilePhotoNotFoundException(UUID photoId) {
		super("Profile photo not found: " + photoId);
	}
}
