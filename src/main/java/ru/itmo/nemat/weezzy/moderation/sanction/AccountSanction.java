package ru.itmo.nemat.weezzy.moderation.sanction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_sanctions")
@Getter
@Setter
public class AccountSanction {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID targetUserId;

	@Column(updatable = false)
	private UUID targetProfileId;

	@Column(updatable = false)
	private UUID sourceReportId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 30)
	private AccountSanctionType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountSanctionStatus status = AccountSanctionStatus.ACTIVE;

	@Column(nullable = false, updatable = false, length = 1000)
	private String reason;

	@Column(updatable = false)
	private LocalDateTime expiresAt;

	@Column(nullable = false, updatable = false)
	private UUID createdByUserId;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private LocalDateTime revokedAt;

	private UUID revokedByUserId;

	@Column(length = 1000)
	private String revocationReason;

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
