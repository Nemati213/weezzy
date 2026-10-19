package ru.itmo.nemat.weezzy.team.application;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class TeamApplicationNotFoundException extends NotFoundException {
	public TeamApplicationNotFoundException(UUID id) {
		super("Team application not found: " + id);
	}
}
