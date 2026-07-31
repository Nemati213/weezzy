package ru.itmo.nemat.weezzy.connection.match;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class MatchNotFoundException extends NotFoundException {
	public MatchNotFoundException(UUID firstProfileId, UUID secondProfileId) {
		super("Match not found between profiles: "
				+ firstProfileId + " and " + secondProfileId);
	}
}
