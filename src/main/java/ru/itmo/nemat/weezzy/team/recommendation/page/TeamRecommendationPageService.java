package ru.itmo.nemat.weezzy.team.recommendation.page;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.itmo.nemat.weezzy.team.recommendation.TeamRecommendation;
import ru.itmo.nemat.weezzy.team.recommendation.TeamRecommendationService;
import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationPageResponse;
import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationResponse;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntry;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntryRepository;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionNotFoundException;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionRepository;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Validated
@RequiredArgsConstructor
public class TeamRecommendationPageService {
	private final TeamRecommendationSessionRepository sessions;
	private final TeamRecommendationEntryRepository entries;
	private final TeamRecommendationService recommendations;
	private final TeamRecommendationCursorCodec cursors;
	private final AccountAccessService accounts;
	private final Clock clock;

	@Transactional
	public TeamRecommendationPageResponse findForUser(
			@NotNull UUID userId, @NotNull UUID sessionId, String cursor
	) {
		accounts.ensureAccessAllowed(userId);
		var session = sessions.findOwnedByIdForUpdate(sessionId, userId)
				.orElseThrow(TeamRecommendationSessionNotFoundException::new);
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
		if (!session.getExpiresAt().isAfter(now)) {
			throw new TeamRecommendationSessionNotFoundException();
		}
		int start = cursors.startOrdinal(cursor, sessionId,
				session.getPageSize(), session.getEntryCount());
		int end = (int) Math.min((long) start + session.getPageSize(),
				session.getEntryCount());
		var window = entries.findWindow(sessionId, start, end);
		var teamIds = window.stream().map(TeamRecommendationEntry::getTeamId)
				.filter(Objects::nonNull).toList();
		List<TeamRecommendation> current = teamIds.isEmpty() ? List.of()
				: recommendations.findForUser(userId, teamIds, now);
		var byTeam = current.stream().collect(Collectors.toMap(
				recommendation -> recommendation.team().id(), Function.identity()));
		var items = window.stream()
				.filter(entry -> byTeam.containsKey(entry.getTeamId()))
				.map(entry -> TeamRecommendationResponse.from(
						entry.getId(), byTeam.get(entry.getTeamId())))
				.toList();
		if (!items.isEmpty()) {
			entries.markDelivered(sessionId,
					items.stream().map(TeamRecommendationResponse::entryId).toList(), now);
		}
		return new TeamRecommendationPageResponse(
				sessionId, session.getCreatedAt(), session.getExpiresAt(),
				session.getPageSize(), items, cursors.encode(sessionId, start),
				end < session.getEntryCount() ? cursors.encode(sessionId, end) : null,
				start > 0 ? cursors.encode(sessionId, start - session.getPageSize()) : null);
	}
}
