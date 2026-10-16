package ru.itmo.nemat.weezzy.team.request;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class TeamRequestClosedException extends ConflictException {
	public TeamRequestClosedException(UUID id) {
		super("Team request is closed: " + id);
	}
}
