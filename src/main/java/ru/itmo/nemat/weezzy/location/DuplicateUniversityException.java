package ru.itmo.nemat.weezzy.location;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class DuplicateUniversityException extends ConflictException {
	public DuplicateUniversityException(String name, String city) {
		super("University already exists: " + name + ", " + city);
	}
}
