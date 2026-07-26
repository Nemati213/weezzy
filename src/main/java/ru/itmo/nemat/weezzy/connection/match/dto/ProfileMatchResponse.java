package ru.itmo.nemat.weezzy.connection.match.dto;

import ru.itmo.nemat.weezzy.connection.match.ProfileMatch;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProfileMatchResponse(
		UUID firstProfileId,
		UUID secondProfileId,
		LocalDateTime createdAt
) {
	public static ProfileMatchResponse from(ProfileMatch profileMatch) {
		return new ProfileMatchResponse(
				profileMatch.getFirstProfileId(),
				profileMatch.getSecondProfileId(),
				profileMatch.getCreatedAt()
		);
	}
}
