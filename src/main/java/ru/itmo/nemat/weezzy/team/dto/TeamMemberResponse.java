package ru.itmo.nemat.weezzy.team.dto;

import ru.itmo.nemat.weezzy.profile.photo.dto.ProfilePhotoResponse;
import ru.itmo.nemat.weezzy.team.member.TeamMember;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TeamMemberResponse(
		UUID profileId,
		String displayName,
		UUID positionId,
		String positionName,
		LocalDateTime joinedAt,
		List<ProfilePhotoResponse> photos
) {
	public static TeamMemberResponse from(
			TeamMember member,
			List<ProfilePhotoResponse> photos
	) {
		return new TeamMemberResponse(
				member.getProfile().getId(),
				member.getProfile().getDisplayName(),
				member.getPosition().getId(),
				member.getPosition().getName(),
				member.getJoinedAt(),
				List.copyOf(photos)
		);
	}
}
