package ru.itmo.nemat.weezzy.team.recommendation.impression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.lock.AccountProfileLockService;
import ru.itmo.nemat.weezzy.team.application.TeamApplicationProperties;
import ru.itmo.nemat.weezzy.team.recommendation.candidate.TeamRecommendationCandidateRepository;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntry;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntryRepository;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSession;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionNotFoundException;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionRepository;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamRecommendationImpressionServiceTests {
	private final AccountProfileLockService locks = mock(AccountProfileLockService.class);
	private final AccountAccessService accounts = mock(AccountAccessService.class);
	private final ProfileService profiles = mock(ProfileService.class);
	private final TeamRecommendationSessionRepository sessions = mock(TeamRecommendationSessionRepository.class);
	private final TeamRecommendationEntryRepository entries = mock(TeamRecommendationEntryRepository.class);
	private final TeamRecommendationCandidateRepository candidates = mock(TeamRecommendationCandidateRepository.class);
	private final TeamRecommendationImpressionWriteRepository writer = mock(TeamRecommendationImpressionWriteRepository.class);
	private final Clock clock = mock(Clock.class);
	private final TeamRecommendationImpressionService service = new TeamRecommendationImpressionService(
			locks, accounts, profiles, sessions, entries, candidates,
			new TeamApplicationProperties(Duration.ofHours(24)), writer, clock);
	private final UUID user = UUID.randomUUID();
	private final UUID sessionId = UUID.randomUUID();
	private final LocalDateTime now = LocalDateTime.of(2026, 10, 30, 12, 0);
	private TeamRecommendationSession session;

	@BeforeEach
	void setUp() {
		session = new TeamRecommendationSession();
		session.setExpiresAt(now.plusHours(1));
		when(sessions.findOwnedByIdForUpdate(sessionId, user)).thenReturn(Optional.of(session));
		when(clock.getZone()).thenReturn(ZoneOffset.UTC);
		when(clock.instant()).thenReturn(now.toInstant(ZoneOffset.UTC));
	}

	@Test
	void writesNewEligibleEntriesInOneBatchAfterAccountAndSessionLocks() {
		var first = entry();
		var hidden = entry();
		var repeated = entry();
		repeated.setFirstShownAt(now.minusMinutes(1));
		var requested = List.of(first.getId(), hidden.getId(), repeated.getId());
		var teams = List.of(first.getTeamId(), hidden.getTeamId());
		when(entries.findBySessionIdAndIdIn(sessionId, requested))
				.thenReturn(List.of(first, hidden, repeated));
		when(writer.lockTeams(teams)).thenReturn(teams);
		when(candidates.findEligibleTeamIds(null, now, now.minusDays(1), teams))
				.thenReturn(List.of(first.getTeamId()));

		service.recordForUser(user, sessionId, requested);

		var order = inOrder(locks, accounts, sessions, entries, writer, candidates);
		order.verify(locks).lock(List.of(), user);
		order.verify(accounts).ensureAccessAllowed(user);
		order.verify(sessions).findOwnedByIdForUpdate(sessionId, user);
		order.verify(entries).findBySessionIdAndIdIn(sessionId, requested);
		order.verify(writer).lockTeams(teams);
		order.verify(candidates).findEligibleTeamIds(null, now, now.minusDays(1), teams);
		order.verify(writer).record(user, sessionId, List.of(first.getId(), hidden.getId()),
				List.of(first.getTeamId()), now);
		order.verifyNoMoreInteractions();
	}

	@Test
	void duplicateIdsAreRejectedBeforeAnyWrite() {
		UUID id = UUID.randomUUID();
		assertThatThrownBy(() -> service.recordForUser(user, sessionId, List.of(id, id)))
				.isInstanceOf(DuplicateTeamRecommendationImpressionException.class);
		verifyNoInteractions(locks, sessions, entries, candidates, writer);
	}

	@Test
	void missingOrForeignSessionIsNotFound() {
		when(sessions.findOwnedByIdForUpdate(sessionId, user)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.recordForUser(user, sessionId, List.of(UUID.randomUUID())))
				.isInstanceOf(TeamRecommendationSessionNotFoundException.class);
		verifyNoInteractions(entries, candidates, writer);
	}

	@Test
	void exactExpiryIsNotFound() {
		session.setExpiresAt(now);
		assertThatThrownBy(() -> service.recordForUser(user, sessionId, List.of(UUID.randomUUID())))
				.isInstanceOf(TeamRecommendationSessionNotFoundException.class);
		verifyNoInteractions(entries, candidates, writer);
	}

	@Test
	void missingEntryInvalidatesEntireBatch() {
		var first = entry();
		var ids = List.of(first.getId(), UUID.randomUUID());
		when(entries.findBySessionIdAndIdIn(sessionId, ids)).thenReturn(List.of(first));
		assertThatThrownBy(() -> service.recordForUser(user, sessionId, ids))
				.isInstanceOf(TeamRecommendationEntryNotFoundException.class);
		verifyNoInteractions(candidates, writer);
	}

	@Test
	void undeliveredEntryInvalidatesEntireBatchIncludingAlreadyShownEntries() {
		var shown = entry();
		shown.setFirstShownAt(now.minusMinutes(1));
		var undelivered = entry();
		undelivered.setFirstDeliveredAt(null);
		var ids = List.of(shown.getId(), undelivered.getId());
		when(entries.findBySessionIdAndIdIn(sessionId, ids)).thenReturn(List.of(shown, undelivered));
		assertThatThrownBy(() -> service.recordForUser(user, sessionId, ids))
				.isInstanceOf(TeamRecommendationEntryNotFoundException.class);
		verifyNoInteractions(candidates, writer);
	}

	@Test
	void repeatsAndDeletedTeamsDoNotWriteHistoryOrRecheckEligibility() {
		var shown = entry();
		shown.setFirstShownAt(now.minusMinutes(1));
		var deleted = entry();
		deleted.setTeamId(null);
		var ids = List.of(shown.getId(), deleted.getId());
		when(entries.findBySessionIdAndIdIn(sessionId, ids)).thenReturn(List.of(shown, deleted));
		service.recordForUser(user, sessionId, ids);
		verifyNoInteractions(profiles, candidates, writer);
	}

	@Test
	void deletionWhileWaitingForTeamLockIsSkipped() {
		var entry = entry();
		var ids = List.of(entry.getId());
		when(entries.findBySessionIdAndIdIn(sessionId, ids)).thenReturn(List.of(entry));
		service.recordForUser(user, sessionId, ids);
		verify(writer).lockTeams(List.of(entry.getTeamId()));
		verify(writer, never()).record(any(), any(), any(), any(), any());
		verifyNoInteractions(profiles, candidates);
	}

	@Test
	void expiryWhileWaitingForTeamLockPreventsWrite() {
		var entry = entry();
		var ids = List.of(entry.getId());
		when(entries.findBySessionIdAndIdIn(sessionId, ids)).thenReturn(List.of(entry));
		when(writer.lockTeams(List.of(entry.getTeamId()))).thenAnswer(invocation -> {
			when(clock.instant()).thenReturn(session.getExpiresAt().toInstant(ZoneOffset.UTC));
			return List.of(entry.getTeamId());
		});
		assertThatThrownBy(() -> service.recordForUser(user, sessionId, ids))
				.isInstanceOf(TeamRecommendationSessionNotFoundException.class);
		verify(writer, never()).record(any(), any(), any(), any(), any());
		verifyNoInteractions(profiles, candidates);
	}

	@Test
	void unavailableTeamsAreSkippedUsingCurrentProfileAndDeclineCutoff() {
		UUID profile = UUID.randomUUID();
		var entry = entry();
		var ids = List.of(entry.getId());
		var teams = List.of(entry.getTeamId());
		when(entries.findBySessionIdAndIdIn(sessionId, ids)).thenReturn(List.of(entry));
		when(writer.lockTeams(teams)).thenReturn(teams);
		when(profiles.findOptionalIdByUserId(user)).thenReturn(Optional.of(profile));
		service.recordForUser(user, sessionId, ids);
		verify(candidates).findEligibleTeamIds(profile, now, now.minusDays(1), teams);
		verify(writer, never()).record(any(), any(), any(), any(), any());
	}

	private TeamRecommendationEntry entry() {
		var entry = new TeamRecommendationEntry();
		entry.setId(UUID.randomUUID());
		entry.setTeamId(UUID.randomUUID());
		entry.setFirstDeliveredAt(now.minusMinutes(2));
		return entry;
	}
}
