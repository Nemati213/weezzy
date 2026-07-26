package ru.itmo.nemat.weezzy.connection.vote;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "profile_votes")
@Data
@IdClass(ProfileVoteId.class)
public class ProfileVote {
	@Id
	@Column(nullable = false)
	private UUID sourceProfileId;

	@Id
	@Column(nullable = false)
	private UUID targetProfileId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProfileVoteAction action;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
