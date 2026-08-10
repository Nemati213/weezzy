package ru.itmo.nemat.weezzy.profile.photo;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

import java.util.UUID;

public class PhotoMetadataMismatchException extends BadRequestException {
	public PhotoMetadataMismatchException(UUID photoId) {
		super("Uploaded object metadata does not match profile photo: " + photoId);
	}
}
