package ru.itmo.nemat.weezzy.connection.block.dto;

import ru.itmo.nemat.weezzy.connection.block.ProfileBlock;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.dto.ProfileResponse;

import java.time.LocalDateTime;

public record ProfileBlockResponse(
		ProfileResponse blockedProfile,
		LocalDateTime createdAt
) {
	public static ProfileBlockResponse from(ProfileBlock profileBlock, Profile blockedProfile) {
		return new ProfileBlockResponse(
				ProfileResponse.from(blockedProfile),
				profileBlock.getCreatedAt()
		);
	}
}
