package ru.itmo.nemat.weezzy.lunch.group;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.itmo.nemat.weezzy.location.Location;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lunch_groups")
@Getter
@Setter
@NoArgsConstructor
public class LunchGroup {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "location_id", nullable = false)
	private Location location;

	@Column(name = "time_slot", nullable = false)
	private LocalDateTime timeSlot;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private LunchTopic topic;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private LunchGroupStatus status = LunchGroupStatus.ACTIVE;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "cancellation_reason", length = 50)
	private LunchGroupCancellationReason cancellationReason;

	@Column(name = "lifecycle_checked_at")
	private LocalDateTime lifecycleCheckedAt;

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
