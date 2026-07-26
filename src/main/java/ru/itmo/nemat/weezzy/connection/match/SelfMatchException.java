package ru.itmo.nemat.weezzy.connection.match;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

import java.util.UUID;

public class SelfMatchException extends BadRequestException {
	public SelfMatchException(UUID profileId) {
		super("Profile cannot match with itself: " + profileId);
	}
}
