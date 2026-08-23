package ru.itmo.nemat.weezzy.lunch.chat;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidLunchChatPageRequestException extends BadRequestException {
	public InvalidLunchChatPageRequestException() {
		super("Before and after cursors cannot be used together");
	}
}
