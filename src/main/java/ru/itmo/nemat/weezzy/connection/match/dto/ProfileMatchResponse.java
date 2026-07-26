package ru.itmo.nemat.weezzy.connection.match.dto;

import ru.itmo.nemat.weezzy.connection.match.ProfileMatch;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.dto.ProfileResponse;

import java.time.LocalDateTime;

public record ProfileMatchResponse(
		ProfileResponse matchedProfile,
		LocalDateTime createdAt
) {
	public static ProfileMatchResponse from(ProfileMatch profileMatch, Profile matchedProfile) {
		return new ProfileMatchResponse(
				ProfileResponse.from(matchedProfile),
				profileMatch.getCreatedAt()
		);
	}
}
