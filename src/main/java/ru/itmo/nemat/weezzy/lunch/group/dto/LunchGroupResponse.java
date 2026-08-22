package ru.itmo.nemat.weezzy.lunch.group.dto;

import ru.itmo.nemat.weezzy.location.dto.LocationResponse;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroup;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMember;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupStatus;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LunchGroupResponse(
		UUID id,
		LocationResponse location,
		LocalDateTime timeSlot,
		LunchTopic topic,
		LunchGroupStatus status,
		List<LunchGroupMemberResponse> members,
		LocalDateTime createdAt,
		LocalDateTime completedAt,
		LocalDateTime cancelledAt
) {
	public static LunchGroupResponse from(
			LunchGroup group,
			List<LunchGroupMember> members
	) {
		return new LunchGroupResponse(
				group.getId(),
				LocationResponse.from(group.getLocation()),
				group.getTimeSlot(),
				group.getTopic(),
				group.getStatus(),
				members.stream().map(LunchGroupMemberResponse::from).toList(),
				group.getCreatedAt(),
				group.getCompletedAt(),
				group.getCancelledAt()
		);
	}
}
