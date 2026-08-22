package ru.itmo.nemat.weezzy.lunch.request;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class LunchExtensionOfferMismatchException extends ConflictException {
	public LunchExtensionOfferMismatchException(UUID requestId) {
		super("Lunch extension offer does not match the current offer for request: "
				+ requestId);
	}
}
