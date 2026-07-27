package ru.itmo.nemat.weezzy.user.dto;

import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuthUserResponse(
		UUID id,
		String email,
		UserRole role,
		LocalDateTime createdAt
) {
	public static AuthUserResponse from(User user) {
		return new AuthUserResponse(
				user.getId(),
				user.getEmail(),
				user.getRole(),
				user.getCreatedAt()
		);
	}
}
