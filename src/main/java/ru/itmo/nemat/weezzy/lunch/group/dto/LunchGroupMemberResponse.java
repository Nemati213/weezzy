package ru.itmo.nemat.weezzy.lunch.group.dto;

import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMember;

import java.time.LocalDateTime;
import java.util.UUID;

public record LunchGroupMemberResponse(
		UUID profileId,
		String displayName,
		LocalDateTime joinedAt
) {
	public static LunchGroupMemberResponse from(LunchGroupMember member) {
		return new LunchGroupMemberResponse(
				member.getProfile().getId(),
				member.getProfile().getDisplayName(),
				member.getJoinedAt()
		);
	}
}
