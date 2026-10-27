package ru.itmo.nemat.weezzy.team.recommendation.impression;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "team_recommendation_impressions")
@IdClass(TeamRecommendationImpressionId.class)
@Getter
@Setter
public class TeamRecommendationImpression {
	@Id
	@Column(nullable = false, updatable = false)
	private UUID ownerUserId;

	@Id
	@Column(nullable = false, updatable = false)
	private UUID teamId;

	@Column(nullable = false)
	private LocalDateTime lastShownAt;
}
