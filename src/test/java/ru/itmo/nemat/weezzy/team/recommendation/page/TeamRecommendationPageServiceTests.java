package ru.itmo.nemat.weezzy.team.recommendation.page;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.itmo.nemat.weezzy.common.pagination.CursorTokenCodec;
import ru.itmo.nemat.weezzy.team.recommendation.TeamRecommendation;
import ru.itmo.nemat.weezzy.team.recommendation.TeamRecommendationService;
import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationResponse;
import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationTeamResponse;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntry;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntryRepository;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSession;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionNotFoundException;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionRepository;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TeamRecommendationPageServiceTests {
	private final TeamRecommendationSessionRepository sessions = mock(TeamRecommendationSessionRepository.class);
	private final TeamRecommendationEntryRepository entries = mock(TeamRecommendationEntryRepository.class);
	private final TeamRecommendationService recommendations = mock(TeamRecommendationService.class);
	private final AccountAccessService accounts = mock(AccountAccessService.class);
	private final TeamRecommendationCursorCodec cursors = new TeamRecommendationCursorCodec(new CursorTokenCodec());
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);
	private final LocalDateTime now = LocalDateTime.now(clock);
	private final TeamRecommendationPageService service = new TeamRecommendationPageService(
			sessions, entries, recommendations, cursors, accounts, clock);
	private final UUID userId = UUID.randomUUID();
	private final UUID sessionId = UUID.randomUUID();
	private TeamRecommendationSession session;

	@BeforeEach
	void setUp() {
		session = new TeamRecommendationSession();
		session.setId(sessionId);
		session.setCreatedAt(now.minusHours(1));
		session.setExpiresAt(now.plusHours(23));
		session.setPageSize(3);
		session.setEntryCount(7);
		when(sessions.findOwnedByIdForUpdate(sessionId, userId)).thenReturn(Optional.of(session));
	}

	@Test
	void preservesSnapshotOrderAndMarksOnlyReturnedEntriesInOneBatch() {
		var first = entry(3, UUID.randomUUID());
		var hidden = entry(4, UUID.randomUUID());
		var last = entry(5, UUID.randomUUID());
		when(entries.findWindow(sessionId, 3, 6)).thenReturn(List.of(first, hidden, last));
		var teamIds = List.of(first.getTeamId(), hidden.getTeamId(), last.getTeamId());
		when(recommendations.findForUser(userId, teamIds, now))
				.thenReturn(List.of(recommendation(last), recommendation(first)));

		var page = service.findForUser(userId, sessionId, cursors.encode(sessionId, 3));

		assertThat(page.items()).extracting(TeamRecommendationResponse::entryId)
				.containsExactly(first.getId(), last.getId());
		assertThat(page.pageCursor()).isEqualTo(cursors.encode(sessionId, 3));
		assertThat(page.previousCursor()).isEqualTo(cursors.encode(sessionId, 0));
		assertThat(page.nextCursor()).isEqualTo(cursors.encode(sessionId, 6));
		verify(recommendations).findForUser(userId, teamIds, now);
		verify(entries).markDelivered(sessionId, List.of(first.getId(), last.getId()), now);
		verify(entries).findWindow(sessionId, 3, 6);
		verifyNoMoreInteractions(entries, recommendations);
	}

	@Test
	void emptyMiddleWindowKeepsBothCursorsWithoutFillingFromOtherWindows() {
		when(entries.findWindow(sessionId, 3, 6)).thenReturn(List.of(entry(3, null)));
		var page = service.findForUser(userId, sessionId, cursors.encode(sessionId, 3));
		assertThat(page.items()).isEmpty();
		assertThat(page.previousCursor()).isEqualTo(cursors.encode(sessionId, 0));
		assertThat(page.nextCursor()).isEqualTo(cursors.encode(sessionId, 6));
		verifyNoInteractions(recommendations);
		verify(entries).findWindow(sessionId, 3, 6);
		verifyNoMoreInteractions(entries);
	}

	@Test
	void firstAndPartialLastWindowsHaveCorrectEdges() {
		var first = service.findForUser(userId, sessionId, null);
		assertThat(first.previousCursor()).isNull();
		assertThat(first.nextCursor()).isEqualTo(cursors.encode(sessionId, 3));
		var last = service.findForUser(userId, sessionId, cursors.encode(sessionId, 6));
		assertThat(last.nextCursor()).isNull();
		assertThat(last.previousCursor()).isEqualTo(cursors.encode(sessionId, 3));
		verify(entries).findWindow(sessionId, 6, 7);
	}

	@Test
	void emptySessionHasReusableFirstPageCursor() {
		session.setEntryCount(0);
		var page = service.findForUser(userId, sessionId, null);
		assertThat(page.items()).isEmpty();
		assertThat(page.pageCursor()).isEqualTo(cursors.encode(sessionId, 0));
		assertThat(page.nextCursor()).isNull();
		assertThat(page.previousCursor()).isNull();
		verifyNoInteractions(recommendations);
	}

	@Test
	void lastWindowDoesNotOverflowInteger() {
		session.setPageSize(20);
		session.setEntryCount(Integer.MAX_VALUE);
		var page = service.findForUser(userId, sessionId, cursors.encode(sessionId, 2147483640));
		assertThat(page.nextCursor()).isNull();
		verify(entries).findWindow(sessionId, 2147483640, Integer.MAX_VALUE);
	}

	@Test
	void missingOrForeignSessionIsHiddenBeforeCursorValidation() {
		when(sessions.findOwnedByIdForUpdate(sessionId, userId)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.findForUser(userId, sessionId, "invalid"))
				.isInstanceOf(TeamRecommendationSessionNotFoundException.class);
		verify(accounts).ensureAccessAllowed(userId);
		verifyNoInteractions(entries, recommendations);
	}

	@Test
	void exactExpiryIsHiddenBeforeCursorValidation() {
		session.setExpiresAt(now);
		assertThatThrownBy(() -> service.findForUser(userId, sessionId, "invalid"))
				.isInstanceOf(TeamRecommendationSessionNotFoundException.class);
		verifyNoInteractions(entries, recommendations);
	}

	@Test
	void invalidCursorCannotReadOrMarkAnything() {
		assertThatThrownBy(() -> service.findForUser(userId, sessionId, cursors.encode(sessionId, 1)))
				.isInstanceOf(InvalidTeamRecommendationCursorException.class);
		verifyNoInteractions(entries, recommendations);
	}

	private TeamRecommendationEntry entry(int ordinal, UUID teamId) {
		var entry = new TeamRecommendationEntry();
		entry.setId(UUID.randomUUID());
		entry.setSessionId(sessionId);
		entry.setOrdinal(ordinal);
		entry.setTeamId(teamId);
		return entry;
	}

	private TeamRecommendation recommendation(TeamRecommendationEntry entry) {
		return new TeamRecommendation(new TeamRecommendationTeamResponse(
				entry.getTeamId(), "Team", "Purpose", null, UUID.randomUUID(), List.of()),
				UUID.randomUUID(), List.of());
	}
}
