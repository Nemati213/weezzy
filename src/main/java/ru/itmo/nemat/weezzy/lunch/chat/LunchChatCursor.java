package ru.itmo.nemat.weezzy.lunch.chat;

import java.time.LocalDateTime;
import java.util.UUID;

public record LunchChatCursor(
		UUID groupId,
		LocalDateTime createdAt,
		UUID messageId
) {
}
