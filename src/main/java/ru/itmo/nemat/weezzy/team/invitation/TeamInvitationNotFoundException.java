package ru.itmo.nemat.weezzy.team.invitation;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class TeamInvitationNotFoundException extends NotFoundException {
	public TeamInvitationNotFoundException(UUID id) {
		super("Team invitation not found: " + id);
	}
}
