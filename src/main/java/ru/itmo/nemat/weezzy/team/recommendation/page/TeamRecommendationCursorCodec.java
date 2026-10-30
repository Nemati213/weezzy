package ru.itmo.nemat.weezzy.team.recommendation.page;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.common.pagination.CursorTokenCodec;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TeamRecommendationCursorCodec {
	private static final String CURSOR_TYPE = "team-recommendation-page";
	private static final int MAX_LENGTH = 512;
	private final CursorTokenCodec tokens;

	public String encode(UUID sessionId, int startOrdinal) {
		if (startOrdinal < 0) {
			throw new InvalidTeamRecommendationCursorException();
		}
		return tokens.encode(CURSOR_TYPE,
				List.of(sessionId.toString(), Integer.toString(startOrdinal)));
	}

	public TeamRecommendationCursor decode(String encoded) {
		if (encoded == null) {
			return null;
		}
		if (encoded.isBlank() || encoded.length() > MAX_LENGTH) {
			throw new InvalidTeamRecommendationCursorException();
		}
		try {
			var values = tokens.decode(encoded, CURSOR_TYPE, 2);
			UUID sessionId = UUID.fromString(values.getFirst());
			if (!sessionId.toString().equalsIgnoreCase(values.getFirst())
					|| !values.getLast().matches("0|[1-9][0-9]{0,9}")) {
				throw new InvalidTeamRecommendationCursorException();
			}
			return new TeamRecommendationCursor(
					sessionId, Integer.parseInt(values.getLast()));
		} catch (IllegalArgumentException exception) {
			throw new InvalidTeamRecommendationCursorException();
		}
	}

	public int startOrdinal(
			String encoded, UUID sessionId, int pageSize, int entryCount
	) {
		TeamRecommendationCursor cursor = decode(encoded);
		int start = cursor == null ? 0 : cursor.startOrdinal();
		if (cursor != null && !cursor.sessionId().equals(sessionId)) {
			throw new InvalidTeamRecommendationCursorException();
		}
		if (start % pageSize != 0
				|| (start >= entryCount && !(start == 0 && entryCount == 0))) {
			throw new InvalidTeamRecommendationCursorException();
		}
		return start;
	}
}
