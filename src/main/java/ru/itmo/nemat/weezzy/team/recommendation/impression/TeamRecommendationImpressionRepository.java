package ru.itmo.nemat.weezzy.team.recommendation.impression;

import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
public interface TeamRecommendationImpressionRepository
		extends Repository<TeamRecommendationImpression, TeamRecommendationImpressionId> {
	List<TeamRecommendationImpression> findByOwnerUserIdAndTeamIdIn(
			UUID ownerUserId, Collection<UUID> teamIds
	);
}
