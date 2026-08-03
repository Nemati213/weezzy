package ru.itmo.nemat.weezzy.connection.vote;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.common.pagination.CursorTokenCodec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VoteCursorCodec {
	private static final String CURSOR_TYPE = "vote";

	private final CursorTokenCodec tokenCodec;

	public String encode(VoteCursor cursor) {
		return tokenCodec.encode(CURSOR_TYPE, List.of(
				cursor.createdAt().toString(),
				cursor.targetProfileId().toString()
		));
	}

	public VoteCursor decode(String encoded) {
		if (encoded == null || encoded.isBlank()) {
			return null;
		}

		try {
			List<String> values = tokenCodec.decode(encoded, CURSOR_TYPE, 2);
			return new VoteCursor(
					LocalDateTime.parse(values.get(0)),
					UUID.fromString(values.get(1))
			);
		} catch (RuntimeException exception) {
			throw new InvalidVoteCursorException();
		}
	}
}
