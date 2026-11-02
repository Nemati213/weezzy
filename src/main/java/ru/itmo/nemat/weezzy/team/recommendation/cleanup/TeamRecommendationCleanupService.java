package ru.itmo.nemat.weezzy.team.recommendation.cleanup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.team.recommendation.TeamRecommendationProperties;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class TeamRecommendationCleanupService {
	private final TeamRecommendationCleanupBatchService batches;
	private final TeamRecommendationCleanupMetrics metrics;
	private final TeamRecommendationProperties properties;
	private final Clock clock;

	@Transactional(propagation = Propagation.NEVER)
	public TeamRecommendationCleanupResult cleanup() {
		var sample = metrics.start();
		try {
			LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
			LocalDateTime cutoff = now.minus(properties.impressionRetention());
			var result = new TeamRecommendationCleanupResult(0, 0, 0);
			for (int i = 0; i < properties.cleanup().maxBatchesPerRun(); i++) {
				var batch = batches.deleteBatch(now, cutoff, properties.cleanup().batchSize());
				metrics.recordBatch(batch);
				result = result.plus(batch);
				if (batch.isEmpty()) {
					break;
				}
			}
			metrics.recordSuccess(sample);
			return result;
		} catch (RuntimeException exception) {
			metrics.recordFailure(sample);
			throw exception;
		}
	}
}
