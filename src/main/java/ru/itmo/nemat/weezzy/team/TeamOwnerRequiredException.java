package ru.itmo.nemat.weezzy.team;

import ru.itmo.nemat.weezzy.common.exception.ForbiddenException;

public class TeamOwnerRequiredException extends ForbiddenException {
	public TeamOwnerRequiredException() {
		super("Only the team owner can perform this action");
	}
}
