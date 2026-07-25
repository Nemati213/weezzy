package ru.itmo.nemat.weezzy.interest;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class InterestNotFoundException extends NotFoundException {
	public InterestNotFoundException(UUID id) {
		super("Interest not found: " + id);
	}
}
