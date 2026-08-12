package ru.itmo.nemat.weezzy.security;

import ru.itmo.nemat.weezzy.user.UserRole;

import java.util.UUID;

public record JwtAuthenticatedUser(
		UUID id,
		String email,
		UserRole role,
		long issuedAtEpochMilli
) {
}
