package ru.itmo.nemat.weezzy.team.request;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class TeamRequestProfileNotActiveException extends ConflictException {
	public TeamRequestProfileNotActiveException(UUID profileId) {
		super("Profile must be active to publish or edit recruitment: " + profileId);
	}
}
