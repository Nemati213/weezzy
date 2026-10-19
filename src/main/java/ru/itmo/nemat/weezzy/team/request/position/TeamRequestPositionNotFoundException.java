package ru.itmo.nemat.weezzy.team.request.position;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class TeamRequestPositionNotFoundException extends NotFoundException {
	public TeamRequestPositionNotFoundException(UUID id) {
		super("Team request position not found: " + id);
	}
}
