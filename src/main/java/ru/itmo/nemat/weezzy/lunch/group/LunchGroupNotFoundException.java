package ru.itmo.nemat.weezzy.lunch.group;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class LunchGroupNotFoundException extends NotFoundException {
	public LunchGroupNotFoundException(UUID userId) {
		super("Active lunch group not found for user: " + userId);
	}
}
