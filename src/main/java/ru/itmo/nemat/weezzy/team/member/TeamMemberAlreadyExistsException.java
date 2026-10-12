package ru.itmo.nemat.weezzy.team.member;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class TeamMemberAlreadyExistsException extends ConflictException {
	public TeamMemberAlreadyExistsException() {
		super("Profile is already a member of the team");
	}
}
