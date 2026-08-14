package ru.itmo.nemat.weezzy.location;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class DuplicateLocationException extends ConflictException {
	public DuplicateLocationException(String name, String address) {
		super("Location already exists: " + name + ", " + address);
	}
}
