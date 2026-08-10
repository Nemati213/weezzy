package ru.itmo.nemat.weezzy.profile.photo;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class PhotoTooLargeException extends BadRequestException {
	public PhotoTooLargeException(long sizeBytes, long maxFileSize) {
		super("Profile photo size " + sizeBytes
				+ " exceeds maximum allowed size " + maxFileSize);
	}
}
