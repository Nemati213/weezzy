package ru.itmo.nemat.weezzy.team.invitation;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class TeamInvitationStatusConflictException extends ConflictException {
	public TeamInvitationStatusConflictException(TeamInvitationStatus status) {
		super("Team invitation has already been resolved: " + status);
	}
}
