package ru.itmo.nemat.weezzy.team.request;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class TeamRequestNotFoundException extends NotFoundException {
	public TeamRequestNotFoundException(UUID id) {
		super("Team request not found: " + id);
	}
}
