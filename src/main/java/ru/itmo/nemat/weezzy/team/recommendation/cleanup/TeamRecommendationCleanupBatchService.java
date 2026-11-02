package ru.itmo.nemat.weezzy.team.recommendation.cleanup;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

@Service
@Validated
@RequiredArgsConstructor
public class TeamRecommendationCleanupBatchService {
	private final TeamRecommendationCleanupRepository repository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public TeamRecommendationCleanupResult deleteBatch(
			@NotNull LocalDateTime now, @NotNull LocalDateTime historyCutoff,
			@Min(1) int batchSize
	) {
		var sessionIds = repository.lockExpiredSessions(now, batchSize);
		int entries = 0;
		int sessions = 0;
		if (!sessionIds.isEmpty()) {
			entries = repository.deleteEntries(sessionIds, batchSize);
			sessions = repository.deleteEmptySessions(sessionIds);
		}
		int impressions = repository.deleteOldImpressions(historyCutoff, batchSize);
		return new TeamRecommendationCleanupResult(sessions, entries, impressions);
	}
}
