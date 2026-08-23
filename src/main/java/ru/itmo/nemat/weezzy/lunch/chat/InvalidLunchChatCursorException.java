package ru.itmo.nemat.weezzy.lunch.chat;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidLunchChatCursorException extends BadRequestException {
	public InvalidLunchChatCursorException() {
		super("Invalid lunch chat cursor");
	}
}
