package ru.itmo.nemat.weezzy.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import ru.itmo.nemat.weezzy.profile.Profile;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "teams")
@Getter
@Setter
public class Team {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_profile_id", nullable = false)
	private Profile ownerProfile;

	@NotBlank
	@Size(max = 120)
	@Column(nullable = false, length = 120)
	private String name;

	@Size(max = 2000)
	@Column(length = 2000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TeamStatus status = TeamStatus.ACTIVE;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public boolean isOwnedBy(UUID profileId) {
		return ownerProfile.getId().equals(profileId);
	}

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
