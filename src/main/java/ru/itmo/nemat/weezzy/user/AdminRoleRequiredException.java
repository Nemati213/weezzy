package ru.itmo.nemat.weezzy.user;

import ru.itmo.nemat.weezzy.common.exception.ForbiddenException;

import java.util.UUID;

public class AdminRoleRequiredException extends ForbiddenException {
	public AdminRoleRequiredException(UUID userId) {
		super("Administrator privileges are required for user: " + userId);
	}
}
