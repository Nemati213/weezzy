package ru.itmo.nemat.weezzy.location;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class LocationNotFoundException extends NotFoundException {
	public LocationNotFoundException(UUID id) {
		super("Location not found: " + id);
	}
}
