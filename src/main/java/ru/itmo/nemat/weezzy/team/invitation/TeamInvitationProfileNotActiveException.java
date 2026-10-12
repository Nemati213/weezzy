package ru.itmo.nemat.weezzy.team.invitation;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class TeamInvitationProfileNotActiveException extends ConflictException {
	public TeamInvitationProfileNotActiveException() {
		super("Both profiles must be active to send or accept a team invitation");
	}
}
