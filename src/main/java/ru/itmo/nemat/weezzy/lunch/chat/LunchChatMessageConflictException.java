package ru.itmo.nemat.weezzy.lunch.chat;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class LunchChatMessageConflictException extends ConflictException {
	public LunchChatMessageConflictException(UUID clientMessageId) {
		super("Client message id was already used with different message data: "
				+ clientMessageId);
	}
}
