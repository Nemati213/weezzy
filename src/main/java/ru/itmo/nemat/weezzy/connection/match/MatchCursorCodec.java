package ru.itmo.nemat.weezzy.connection.match;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.common.pagination.CursorTokenCodec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class MatchCursorCodec {
	private static final String CURSOR_TYPE = "match";

	private final CursorTokenCodec tokenCodec;

	String encode(MatchCursor cursor) {
		return tokenCodec.encode(CURSOR_TYPE, List.of(
				cursor.createdAt().toString(),
				cursor.firstProfileId().toString(),
				cursor.secondProfileId().toString()
		));
	}

	MatchCursor decode(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}

		try {
			List<String> values = tokenCodec.decode(cursor, CURSOR_TYPE, 3);
			return new MatchCursor(
					LocalDateTime.parse(values.get(0)),
					UUID.fromString(values.get(1)),
					UUID.fromString(values.get(2))
			);
		} catch (RuntimeException exception) {
			throw new InvalidMatchCursorException();
		}
	}
}
