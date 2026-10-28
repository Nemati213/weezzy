package ru.itmo.nemat.weezzy.team.recommendation.session;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.team.recommendation.TeamRecommendationProperties;
import ru.itmo.nemat.weezzy.user.AccountAccessService;
import ru.itmo.nemat.weezzy.user.UserNotFoundException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamRecommendationSessionCreator {
	private final TeamRecommendationSessionRepository sessions;
	private final TeamRecommendationSessionWriteRepository writer;
	private final TeamRecommendationOrderService orderService;
	private final TeamRecommendationProperties properties;
	private final AccountAccessService accounts;
	private final Clock clock;

	@Transactional(isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
	public TeamRecommendationSessionResult create(UUID userId, UUID clientRequestId, int pageSize) {
		if (!writer.serializeCreation(userId)) {
			throw new UserNotFoundException(userId);
		}
		accounts.ensureAccessAllowed(userId);
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
		var existing = sessions.findByOwnerUserIdAndClientRequestId(userId, clientRequestId);
		if (existing.isPresent()) {
			var session = existing.orElseThrow();
			if (!session.getExpiresAt().isAfter(now)) {
				throw new TeamRecommendationSessionNotFoundException();
			}
			if (session.getPageSize() != pageSize) {
				throw new TeamRecommendationSessionConflictException();
			}
			return TeamRecommendationSessionResult.from(session, false);
		}
		var teamIds = orderService.build(userId, now);
		var session = new TeamRecommendationSession();
		session.setOwnerUserId(userId);
		session.setClientRequestId(clientRequestId);
		session.setCreatedAt(now);
		session.setExpiresAt(now.plus(properties.sessionTtl()).truncatedTo(ChronoUnit.MICROS));
		session.setPageSize(pageSize);
		session.setEntryCount(teamIds.size());
		sessions.saveAndFlush(session);
		writer.insertEntries(session.getId(), teamIds);
		var active = sessions.findActiveByOwner(userId, now).stream()
				.filter(candidate -> !candidate.getId().equals(session.getId())).toList();
		int removeCount = Math.max(0, active.size() + 1 - properties.maxActiveSessionsPerUser());
		if (removeCount > 0) {
			sessions.deleteAllByIdInBatch(active.subList(0, removeCount).stream()
					.map(TeamRecommendationSession::getId).toList());
		}
		return TeamRecommendationSessionResult.from(session, true);
	}
}
