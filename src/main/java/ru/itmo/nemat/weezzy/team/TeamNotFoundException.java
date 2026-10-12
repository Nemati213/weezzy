package ru.itmo.nemat.weezzy.team;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class TeamNotFoundException extends NotFoundException {
	public TeamNotFoundException(UUID teamId) {
		super("Team not found: " + teamId);
	}
}
