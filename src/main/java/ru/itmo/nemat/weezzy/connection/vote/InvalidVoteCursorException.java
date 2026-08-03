package ru.itmo.nemat.weezzy.connection.vote;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidVoteCursorException extends BadRequestException {
	public InvalidVoteCursorException() {
		super("Invalid vote cursor");
	}
}
