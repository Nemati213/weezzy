package ru.itmo.nemat.weezzy.connection.block;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.common.pagination.CursorTokenCodec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class BlockCursorCodec {
	private static final String CURSOR_TYPE = "block";

	private final CursorTokenCodec tokenCodec;

	String encode(BlockCursor cursor) {
		return tokenCodec.encode(CURSOR_TYPE, List.of(
				cursor.createdAt().toString(),
				cursor.blockedProfileId().toString()
		));
	}

	BlockCursor decode(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}

		try {
			List<String> values = tokenCodec.decode(cursor, CURSOR_TYPE, 2);
			return new BlockCursor(
					LocalDateTime.parse(values.get(0)),
					UUID.fromString(values.get(1))
			);
		} catch (RuntimeException exception) {
			throw new InvalidBlockCursorException();
		}
	}
}
