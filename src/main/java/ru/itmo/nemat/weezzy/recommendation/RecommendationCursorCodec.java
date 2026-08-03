package ru.itmo.nemat.weezzy.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.common.pagination.CursorTokenCodec;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class RecommendationCursorCodec {
	private static final String CURSOR_TYPE = "recommendation";

	private final CursorTokenCodec tokenCodec;

	String encode(RecommendationCursor cursor) {
		return tokenCodec.encode(CURSOR_TYPE, List.of(
				Integer.toString(cursor.score()),
				cursor.profileId().toString()
		));
	}

	RecommendationCursor decode(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}

		try {
			List<String> values = tokenCodec.decode(cursor, CURSOR_TYPE, 2);
			return new RecommendationCursor(
					Integer.parseInt(values.get(0)),
					UUID.fromString(values.get(1))
			);
		} catch (RuntimeException exception) {
			throw new InvalidRecommendationCursorException();
		}
	}
}
