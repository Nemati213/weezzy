package ru.itmo.nemat.weezzy.lunch.request.dto;

import ru.itmo.nemat.weezzy.location.dto.LocationResponse;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestStatus;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;

import java.time.LocalDateTime;
import java.util.UUID;

public record LunchRequestResponse(
		UUID id,
		UUID profileId,
		LocationResponse location,
		LunchRequestStatus status,
		LunchTopic topic,
		String comment,
		LocalDateTime timeSlot,
		int extensionCount,
		LocalDateTime extensionRequestedAt,
		LocalDateTime cancelledAt,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static LunchRequestResponse from(LunchRequest request) {
		return new LunchRequestResponse(
				request.getId(),
				request.getProfile().getId(),
				LocationResponse.from(request.getLocation()),
				request.getStatus(),
				request.getTopic(),
				request.getComment(),
				request.getTimeSlot(),
				request.getExtensionCount(),
				request.getExtensionRequestedAt(),
				request.getCancelledAt(),
				request.getCreatedAt(),
				request.getUpdatedAt()
		);
	}
}
