package ru.itmo.nemat.weezzy.connection.block.dto;

import ru.itmo.nemat.weezzy.connection.block.ProfileBlock;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.dto.ProfileResponse;
import ru.itmo.nemat.weezzy.profile.photo.dto.ProfilePhotoResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProfileBlockResponse(
		ProfileResponse blockedProfile,
		LocalDateTime createdAt
) {
	public static ProfileBlockResponse from(
			ProfileBlock profileBlock,
			Profile blockedProfile,
			List<ProfilePhotoResponse> photos
	) {
		return new ProfileBlockResponse(
				ProfileResponse.from(blockedProfile, photos),
				profileBlock.getCreatedAt()
		);
	}

	public static ProfileBlockResponse unavailable(
			ProfileBlock profileBlock,
			UUID blockedProfileId
	) {
		return new ProfileBlockResponse(
				ProfileResponse.unavailable(blockedProfileId),
				profileBlock.getCreatedAt()
		);
	}
}
