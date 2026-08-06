package ru.itmo.nemat.weezzy.user.accountdeletion;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidCurrentPasswordException extends BadRequestException {
	public InvalidCurrentPasswordException() {
		super("Current password is invalid");
	}
}
