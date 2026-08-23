package ru.itmo.nemat.weezzy.lunch.group;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

public class LunchGroupNotFoundException extends NotFoundException {
	public LunchGroupNotFoundException() {
		super("Active lunch group not found");
	}
}
