package ru.itmo.nemat.weezzy.lunch.request;

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
import lombok.Data;
import ru.itmo.nemat.weezzy.location.Location;
import ru.itmo.nemat.weezzy.profile.Profile;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lunch_requests")
@Data
public class LunchRequest {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "profile_id", nullable = false)
	private Profile profile;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "location_id", nullable = false)
	private Location location;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private LunchRequestStatus status = LunchRequestStatus.SEARCHING;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private LunchTopic topic;

	@Column(length = 255)
	private String comment;

	@Column(name = "time_slot", nullable = false)
	private LocalDateTime timeSlot;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;

	@Column(name = "extension_requested_at")
	private LocalDateTime extensionRequestedAt;

	@Column(name = "extension_count", nullable = false)
	private int extensionCount;

	@PreUpdate
	void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}
}
