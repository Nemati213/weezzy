package ru.itmo.nemat.weezzy.team;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class TeamOwnerCannotLeaveException extends ConflictException {
	public TeamOwnerCannotLeaveException() {
		super("The team owner cannot leave or be removed; archive the team instead");
	}
}
