package ru.itmo.nemat.weezzy.team.invitation;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class DuplicatePendingTeamInvitationException extends ConflictException {
	public DuplicatePendingTeamInvitationException() {
		super("A pending invitation for this profile already exists in the team");
	}
}
