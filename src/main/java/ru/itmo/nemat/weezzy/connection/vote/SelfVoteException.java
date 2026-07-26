package ru.itmo.nemat.weezzy.connection.vote;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

import java.util.UUID;

public class SelfVoteException extends BadRequestException {
	public SelfVoteException(UUID profileId) {
		super("Profile cannot vote for itself: " + profileId);
	}
}
