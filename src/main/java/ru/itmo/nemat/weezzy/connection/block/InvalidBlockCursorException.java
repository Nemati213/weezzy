package ru.itmo.nemat.weezzy.connection.block;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidBlockCursorException extends BadRequestException {
	public InvalidBlockCursorException() {
		super("Invalid block cursor");
	}
}
