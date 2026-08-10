package ru.itmo.nemat.weezzy.profile.photo;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidPhotoOrderException extends BadRequestException {
	public InvalidPhotoOrderException() {
		super("Photo order must contain every ready profile photo exactly once");
	}
}
