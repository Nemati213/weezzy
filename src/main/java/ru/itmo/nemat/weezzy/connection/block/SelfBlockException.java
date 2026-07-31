package ru.itmo.nemat.weezzy.connection.block;


import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

import java.util.UUID;

public class SelfBlockException extends BadRequestException {
	public SelfBlockException(UUID profileId) {
		super("Profile cannot block itself: " + profileId);
	}
}
