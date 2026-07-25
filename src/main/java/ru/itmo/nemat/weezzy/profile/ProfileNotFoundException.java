package ru.itmo.nemat.weezzy.profile;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class ProfileNotFoundException extends NotFoundException {
	public ProfileNotFoundException(UUID id) {
		super("Profile not found: " + id);
	}
}
