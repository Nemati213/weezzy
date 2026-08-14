package ru.itmo.nemat.weezzy.location;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class UniversityNotFoundException extends NotFoundException {
	public UniversityNotFoundException(UUID id) {
		super("University not found: " + id);
	}
}
