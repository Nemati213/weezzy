package ru.itmo.nemat.weezzy.recommendation.impression;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.event.ProfileInteractionEventService;
import ru.itmo.nemat.weezzy.connection.event.ProfileInteractionEventType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileRecommendationImpressionService {
	private final ProfileRecommendationImpressionRepository impressionRepository;
	private final ProfileInteractionEventService interactionEventService;

	@Transactional
	public void recordImpressions(UUID sourceProfileId, List<UUID> targetProfileIds) {
		LocalDateTime shownAt = LocalDateTime.now();
		targetProfileIds.forEach(targetProfileId ->
				impressionRepository.upsert(sourceProfileId, targetProfileId, shownAt)
		);
		interactionEventService.recordAll(
				sourceProfileId,
				targetProfileIds,
				ProfileInteractionEventType.RECOMMENDATION_IMPRESSION
		);
	}

}
