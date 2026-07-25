package ru.itmo.nemat.weezzy.goal;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class GoalNotFoundException extends NotFoundException {
	public GoalNotFoundException(UUID id) {
		super("Goal not found: " + id);
	}
}
