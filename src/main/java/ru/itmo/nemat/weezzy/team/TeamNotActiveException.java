package ru.itmo.nemat.weezzy.team;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class TeamNotActiveException extends ConflictException {
	public TeamNotActiveException(UUID teamId) {
		super("Team is not active: " + teamId);
	}
}
