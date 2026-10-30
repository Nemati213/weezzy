package ru.itmo.nemat.weezzy.team.recommendation.page;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.itmo.nemat.weezzy.common.pagination.CursorTokenCodec;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamRecommendationCursorCodecTests {
	private final TeamRecommendationCursorCodec codec =
			new TeamRecommendationCursorCodec(new CursorTokenCodec());
	private final UUID session = UUID.randomUUID();

	@Test
	void roundTripAndFirstPageOfEmptySession() {
		assertThat(codec.decode(codec.encode(session, 40)))
				.isEqualTo(new TeamRecommendationCursor(session, 40));
		assertThat(codec.startOrdinal(null, session, 20, 0)).isZero();
		assertThat(codec.startOrdinal(codec.encode(session, 0), session, 20, 0)).isZero();
		assertThat(codec.startOrdinal(codec.encode(session, 40), session, 20, 41)).isEqualTo(40);
		assertThat(codec.startOrdinal(codec.encode(session, 2147483640),
				session, 20, Integer.MAX_VALUE)).isEqualTo(2147483640);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "%%%", "a", "eA==", "v1|team-recommendation-page"})
	void rejectsInvalidEncoding(String encoded) {
		assertThatThrownBy(() -> codec.decode(encoded))
				.isInstanceOf(InvalidTeamRecommendationCursorException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"-1", "+1", "01", "1.0", "1e2", "", " 0", "0 ",
			"2147483648", "99999999999", "one", "1|2"})
	void rejectsInvalidOrdinal(String ordinal) {
		assertThatThrownBy(() -> codec.decode(raw("v1|team-recommendation-page|" + session + "|" + ordinal)))
				.isInstanceOf(InvalidTeamRecommendationCursorException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"v2|team-recommendation-page|%s|0",
			"v1|another-type|%s|0", "v1|team-recommendation-page|%s",
			"v1|team-recommendation-page|%s|0|extra",
			"v1|team-recommendation-page|1-1-1-1-1|0",
			"v1|team-recommendation-page|invalid|0"})
	void rejectsWrongShapeVersionTypeOrUuid(String value) {
		assertThatThrownBy(() -> codec.decode(raw(value.formatted(session))))
				.isInstanceOf(InvalidTeamRecommendationCursorException.class);
	}

	@Test
	void rejectsOversizedCursorAndNegativeEncoding() {
		assertThatThrownBy(() -> codec.decode("A".repeat(513)))
				.isInstanceOf(InvalidTeamRecommendationCursorException.class);
		assertThatThrownBy(() -> codec.encode(session, -1))
				.isInstanceOf(InvalidTeamRecommendationCursorException.class);
	}

	@Test
	void rejectsAnotherSession() {
		assertThatThrownBy(() -> codec.startOrdinal(codec.encode(UUID.randomUUID(), 0), session, 20, 40))
				.isInstanceOf(InvalidTeamRecommendationCursorException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 19, 21, 40, 60})
	void rejectsMisalignedAndOutOfRangeWindows(int start) {
		assertThatThrownBy(() -> codec.startOrdinal(codec.encode(session, start), session, 20, 40))
				.isInstanceOf(InvalidTeamRecommendationCursorException.class);
	}

	private String raw(String value) {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}
}
