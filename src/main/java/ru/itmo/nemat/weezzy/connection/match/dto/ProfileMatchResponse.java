package ru.itmo.nemat.weezzy.connection.match.dto;

import ru.itmo.nemat.weezzy.connection.match.ProfileMatch;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.dto.ProfileResponse;
import ru.itmo.nemat.weezzy.profile.photo.dto.ProfilePhotoResponse;

import java.time.LocalDateTime;
import java.util.List;

public record ProfileMatchResponse(
		ProfileResponse matchedProfile,
		LocalDateTime createdAt
) {
	public static ProfileMatchResponse from(
			ProfileMatch profileMatch,
			Profile matchedProfile,
			List<ProfilePhotoResponse> photos
	) {
		return new ProfileMatchResponse(
				ProfileResponse.withContact(matchedProfile, photos),
				profileMatch.getCreatedAt()
		);
	}
}
