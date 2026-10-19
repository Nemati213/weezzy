package ru.itmo.nemat.weezzy.team.position;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class TeamPositionNotFoundException extends NotFoundException {
	public TeamPositionNotFoundException(UUID id) {
		super("Team position not found: " + id);
	}
}
