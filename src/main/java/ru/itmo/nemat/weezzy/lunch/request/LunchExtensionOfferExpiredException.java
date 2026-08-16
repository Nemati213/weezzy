package ru.itmo.nemat.weezzy.lunch.request;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class LunchExtensionOfferExpiredException extends ConflictException {
	public LunchExtensionOfferExpiredException(UUID requestId) {
		super("Lunch extension offer has expired for request: " + requestId);
	}
}
