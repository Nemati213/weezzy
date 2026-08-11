package ru.itmo.nemat.weezzy.moderation.report;

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
import lombok.Setter;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "profile_reports")
@Getter
@Setter
public class ProfileReport {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reporter_profile_id", nullable = false)
	private Profile reporterProfile;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "target_profile_id", nullable = false)
	private Profile targetProfile;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private ProfileReportReason reason;

	@Column(length = 1000)
	private String comment;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProfileReportStatus status = ProfileReportStatus.PENDING;

	@Column(length = 1000)
	private String decision;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reviewed_by_user_id")
	private User reviewedBy;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private LocalDateTime reviewedAt;

	private LocalDateTime closedAt;

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
