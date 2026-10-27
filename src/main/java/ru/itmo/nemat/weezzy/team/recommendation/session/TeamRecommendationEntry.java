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
@Table(name = "team_recommendation_entries")
@Getter
@Setter
public class TeamRecommendationEntry {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID sessionId;

	@Column(nullable = false, updatable = false)
	private int ordinal;

	@Column(updatable = false)
	private UUID teamId;

	private LocalDateTime firstDeliveredAt;
	private LocalDateTime firstShownAt;
}
