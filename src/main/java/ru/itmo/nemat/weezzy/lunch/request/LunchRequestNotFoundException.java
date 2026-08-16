package ru.itmo.nemat.weezzy.lunch.request;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class LunchRequestNotFoundException extends NotFoundException {
	public LunchRequestNotFoundException(UUID profileId) {
		super("Lunch request not found for profile: " + profileId);
	}
}
