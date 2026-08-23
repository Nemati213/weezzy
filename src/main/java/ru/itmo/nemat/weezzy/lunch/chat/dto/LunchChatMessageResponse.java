package ru.itmo.nemat.weezzy.lunch.chat.dto;

import ru.itmo.nemat.weezzy.lunch.chat.LunchChatMessage;

import java.time.LocalDateTime;
import java.util.UUID;

public record LunchChatMessageResponse(
		UUID id,
		UUID groupId,
		UUID senderProfileId,
		String senderDisplayName,
		String content,
		LocalDateTime createdAt
) {
	public static LunchChatMessageResponse from(LunchChatMessage message) {
		return new LunchChatMessageResponse(
				message.getId(),
				message.getGroup().getId(),
				message.getSenderProfile().getId(),
				message.getSenderProfile().getDisplayName(),
				message.getContent(),
				message.getCreatedAt()
		);
	}
}
