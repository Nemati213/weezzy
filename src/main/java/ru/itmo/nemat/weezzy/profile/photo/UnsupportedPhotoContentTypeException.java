package ru.itmo.nemat.weezzy.profile.photo;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class UnsupportedPhotoContentTypeException extends BadRequestException {
	public UnsupportedPhotoContentTypeException(String contentType) {
		super("Unsupported profile photo content type: " + contentType);
	}
}
