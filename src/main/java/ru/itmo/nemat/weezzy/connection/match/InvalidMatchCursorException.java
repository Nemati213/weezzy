package ru.itmo.nemat.weezzy.connection.match;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidMatchCursorException extends BadRequestException {
	public InvalidMatchCursorException() {
		super("Invalid match cursor");
	}
}
