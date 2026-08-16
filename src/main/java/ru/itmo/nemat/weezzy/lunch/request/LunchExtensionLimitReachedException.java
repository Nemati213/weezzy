package ru.itmo.nemat.weezzy.lunch.request;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class LunchExtensionLimitReachedException extends ConflictException {
	public LunchExtensionLimitReachedException(UUID requestId, int maxExtensions) {
		super("Lunch request " + requestId + " reached the extension limit: "
				+ maxExtensions);
	}
}
