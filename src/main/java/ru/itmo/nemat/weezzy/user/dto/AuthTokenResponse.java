package ru.itmo.nemat.weezzy.user.dto;

import ru.itmo.nemat.weezzy.user.User;

public record AuthTokenResponse(
		String accessToken,
		String tokenType,
		AuthUserResponse user
) {
	public static AuthTokenResponse bearer(String accessToken, User user) {
		return new AuthTokenResponse(
				accessToken,
				"Bearer",
				AuthUserResponse.from(user)
		);
	}
}
