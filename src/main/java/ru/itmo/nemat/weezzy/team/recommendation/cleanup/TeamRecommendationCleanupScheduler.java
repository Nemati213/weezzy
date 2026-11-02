package ru.itmo.nemat.weezzy.team.recommendation.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.team-recommendation.cleanup.enabled",
		havingValue = "true", matchIfMissing = true)
public class TeamRecommendationCleanupScheduler {
	private final TeamRecommendationCleanupService service;

	@Scheduled(fixedDelayString = "${app.team-recommendation.cleanup.interval:PT15M}")
	public void cleanup() {
		var result = service.cleanup();
		if (!result.isEmpty()) {
			log.info("Deleted team recommendation data: {} sessions, {} entries, {} impressions",
					result.sessions(), result.entries(), result.impressions());
		}
	}
}
