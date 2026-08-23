package ru.itmo.nemat.weezzy.lunch.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.common.pagination.CursorTokenCodec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class LunchChatCursorCodec {
	private static final String CURSOR_TYPE = "lunch-chat-message";

	private final CursorTokenCodec tokenCodec;

	String encode(LunchChatCursor cursor) {
		return tokenCodec.encode(CURSOR_TYPE, List.of(
				cursor.groupId().toString(),
				cursor.createdAt().toString(),
				cursor.messageId().toString()
		));
	}

	LunchChatCursor decode(String encoded) {
		if (encoded == null || encoded.isBlank()) {
			return null;
		}

		try {
			List<String> values = tokenCodec.decode(encoded, CURSOR_TYPE, 3);
			return new LunchChatCursor(
					UUID.fromString(values.get(0)),
					LocalDateTime.parse(values.get(1)),
					UUID.fromString(values.get(2))
			);
		} catch (RuntimeException exception) {
			throw new InvalidLunchChatCursorException();
		}
	}
}
