package ru.itmo.nemat.weezzy.lunch.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.itmo.nemat.weezzy.lunch.chat.LunchChatMessage;

import java.util.UUID;

public record CreateLunchChatMessageRequest(
		@NotNull UUID clientMessageId,
		@NotBlank
		@Size(max = LunchChatMessage.MAX_CONTENT_LENGTH)
		String content
) {
	public CreateLunchChatMessageRequest {
		if (content != null) {
			content = content.strip();
		}
	}
}
