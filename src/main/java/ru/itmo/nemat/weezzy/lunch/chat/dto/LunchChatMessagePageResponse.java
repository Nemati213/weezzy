package ru.itmo.nemat.weezzy.lunch.chat.dto;

import java.util.List;

public record LunchChatMessagePageResponse(
		List<LunchChatMessageResponse> content,
		String nextBeforeCursor,
		String nextAfterCursor
) {
}
