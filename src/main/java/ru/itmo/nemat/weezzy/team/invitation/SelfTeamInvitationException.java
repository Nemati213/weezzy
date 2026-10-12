package ru.itmo.nemat.weezzy.team.invitation;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class SelfTeamInvitationException extends BadRequestException {
	public SelfTeamInvitationException() {
		super("Cannot invite yourself to a team");
	}
}
