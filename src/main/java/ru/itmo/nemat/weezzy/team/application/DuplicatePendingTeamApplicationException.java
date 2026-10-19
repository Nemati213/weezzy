package ru.itmo.nemat.weezzy.team.application;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class DuplicatePendingTeamApplicationException extends ConflictException {
	public DuplicatePendingTeamApplicationException() {
		super("A pending application for this position already exists");
	}
}
