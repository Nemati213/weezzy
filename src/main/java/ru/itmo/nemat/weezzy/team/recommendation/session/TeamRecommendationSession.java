package ru.itmo.nemat.weezzy.team.recommendation.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "team_recommendation_sessions")
@Getter
@Setter
public class TeamRecommendationSession {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID ownerUserId;

	@Column(nullable = false, updatable = false)
	private UUID clientRequestId;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false, updatable = false)
	private LocalDateTime expiresAt;

	@Column(nullable = false, updatable = false)
	private int pageSize;

	@Column(nullable = false, updatable = false)
	private int entryCount;
}
