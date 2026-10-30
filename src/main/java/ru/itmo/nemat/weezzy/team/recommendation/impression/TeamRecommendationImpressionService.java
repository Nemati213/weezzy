package ru.itmo.nemat.weezzy.team.recommendation.impression;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.lock.AccountProfileLockService;
import ru.itmo.nemat.weezzy.team.application.TeamApplicationProperties;
import ru.itmo.nemat.weezzy.team.recommendation.candidate.TeamRecommendationCandidateRepository;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntry;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationEntryRepository;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionNotFoundException;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionRepository;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
public class TeamRecommendationImpressionService {
	private final AccountProfileLockService locks;
	private final AccountAccessService accounts;
	private final ProfileService profiles;
	private final TeamRecommendationSessionRepository sessions;
	private final TeamRecommendationEntryRepository entries;
	private final TeamRecommendationCandidateRepository candidates;
	private final TeamApplicationProperties applicationProperties;
	private final TeamRecommendationImpressionWriteRepository writer;
	private final Clock clock;

	@Transactional
	public void recordForUser(
			@NotNull UUID userId, @NotNull UUID sessionId,
			@NotNull @Size(min = 1, max = 50) List<@NotNull UUID> entryIds
	) {
		if (new HashSet<>(entryIds).size() != entryIds.size()) {
			throw new DuplicateTeamRecommendationImpressionException();
		}
		locks.lock(List.of(), userId);
		accounts.ensureAccessAllowed(userId);
		var session = sessions.findOwnedByIdForUpdate(sessionId, userId)
				.orElseThrow(TeamRecommendationSessionNotFoundException::new);
		ensureActive(session.getExpiresAt());
		var requested = entries.findBySessionIdAndIdIn(sessionId, entryIds);
		if (requested.size() != entryIds.size()
				|| requested.stream().anyMatch(entry -> entry.getFirstDeliveredAt() == null)) {
			throw new TeamRecommendationEntryNotFoundException();
		}
		var pending = requested.stream().filter(entry -> entry.getFirstShownAt() == null
				&& entry.getTeamId() != null).toList();
		if (pending.isEmpty()) {
			return;
		}
		var teamIds = writer.lockTeams(pending.stream()
				.map(TeamRecommendationEntry::getTeamId).toList());
		LocalDateTime now = ensureActive(session.getExpiresAt());
		if (teamIds.isEmpty()) {
			return;
		}
		UUID profileId = profiles.findOptionalIdByUserId(userId).orElse(null);
		var available = candidates.findEligibleTeamIds(profileId, now,
				now.minus(applicationProperties.reapplicationCooldown()), teamIds);
		if (!available.isEmpty()) {
			writer.record(userId, sessionId, pending.stream()
					.map(TeamRecommendationEntry::getId).toList(), available, now);
		}
	}

	private LocalDateTime ensureActive(LocalDateTime expiresAt) {
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
		if (!expiresAt.isAfter(now)) {
			throw new TeamRecommendationSessionNotFoundException();
		}
		return now;
	}
}
