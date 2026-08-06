package ru.itmo.nemat.weezzy.user.dto;

import ru.itmo.nemat.weezzy.user.User;

import java.time.LocalDateTime;

public record AuthTokenResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresIn,
		LocalDateTime refreshTokenExpiresAt,
		AuthUserResponse user
) {
	public static AuthTokenResponse bearer(
			String accessToken,
			String refreshToken,
			long expiresIn,
			LocalDateTime refreshTokenExpiresAt,
			User user
	) {
		return new AuthTokenResponse(
				accessToken,
				refreshToken,
				"Bearer",
				expiresIn,
				refreshTokenExpiresAt,
				AuthUserResponse.from(user)
		);
	}
}
