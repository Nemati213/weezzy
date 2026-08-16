package ru.itmo.nemat.weezzy.lunch.request;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class InvalidLunchRequestStateException extends ConflictException {
	public InvalidLunchRequestStateException(
			UUID requestId,
			LunchRequestStatus status,
			String operation
	) {
		super("Cannot " + operation + " lunch request " + requestId
				+ " in status " + status);
	}
}
