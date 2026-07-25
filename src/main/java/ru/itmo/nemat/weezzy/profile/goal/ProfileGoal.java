package ru.itmo.nemat.weezzy.profile.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "profile_goals")
@IdClass(ProfileGoalId.class)
@Data
public class ProfileGoal {
	@Id
	@Column(nullable = false)
	private UUID profileId;

	@Id
	@Column(nullable = false)
	private UUID goalId;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
