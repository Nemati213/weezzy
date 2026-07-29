package ru.itmo.nemat.weezzy.recommendation.impression;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "profile_recommendation_impressions")
@Data
@IdClass(ProfileRecommendationImpressionId.class)
public class ProfileRecommendationImpression {
	@Id
	@Column(nullable = false)
	private UUID sourceProfileId;

	@Id
	@Column(nullable = false)
	private UUID targetProfileId;

	@Column(nullable = false)
	private LocalDateTime shownAt;
}
