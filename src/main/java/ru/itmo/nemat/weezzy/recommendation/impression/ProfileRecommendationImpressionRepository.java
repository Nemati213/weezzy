package ru.itmo.nemat.weezzy.recommendation.impression;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ProfileRecommendationImpressionRepository
		extends JpaRepository<ProfileRecommendationImpression, ProfileRecommendationImpressionId> {
	void deleteAllBySourceProfileIdOrTargetProfileId(
			UUID sourceProfileId,
			UUID targetProfileId
	);

	@Modifying
	@Query(value = """
			INSERT INTO profile_recommendation_impressions (
			    source_profile_id,
			    target_profile_id,
			    shown_at
			)
			VALUES (:sourceProfileId, :targetProfileId, :shownAt)
			ON CONFLICT (source_profile_id, target_profile_id)
			DO UPDATE SET shown_at = EXCLUDED.shown_at
			""", nativeQuery = true)
	void upsert(
			@Param("sourceProfileId") UUID sourceProfileId,
			@Param("targetProfileId") UUID targetProfileId,
			@Param("shownAt") LocalDateTime shownAt
	);
}
