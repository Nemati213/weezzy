package ru.itmo.nemat.weezzy.team.position;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class TeamPositionCapacityException extends ConflictException {
	public TeamPositionCapacityException() {
		super("Team position capacity is exhausted or smaller than its membership");
	}
}
