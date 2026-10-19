package ru.itmo.nemat.weezzy.team.application;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class TeamApplicationStatusConflictException extends ConflictException {
	public TeamApplicationStatusConflictException(TeamApplicationStatus status) {
		super("Team application is already resolved: " + status);
	}
}
