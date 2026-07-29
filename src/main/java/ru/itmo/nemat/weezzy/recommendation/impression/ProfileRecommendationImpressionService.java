package ru.itmo.nemat.weezzy.recommendation.impression;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileRecommendationImpressionService {
	private final ProfileRecommendationImpressionRepository impressionRepository;

	@Transactional
	public void recordImpressions(UUID sourceProfileId, List<UUID> targetProfileIds) {
		LocalDateTime shownAt = LocalDateTime.now();
		targetProfileIds.forEach(targetProfileId ->
				impressionRepository.upsert(sourceProfileId, targetProfileId, shownAt)
		);
	}

}
