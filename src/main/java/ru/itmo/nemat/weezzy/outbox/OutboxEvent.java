package ru.itmo.nemat.weezzy.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {
	public static final int LAST_ERROR_MAX_LENGTH = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 50)
	private OutboxEventType eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false, columnDefinition = "jsonb")
	private Map<String, Object> payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OutboxEventStatus status = OutboxEventStatus.PENDING;

	@Column(nullable = false)
	private int attemptCount;

	@Column(nullable = false)
	private LocalDateTime nextAttemptAt;

	private LocalDateTime lockedAt;

	@Column(length = 100)
	private String lockedBy;

	@Column(length = LAST_ERROR_MAX_LENGTH)
	private String lastError;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private LocalDateTime processedAt;

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		createdAt = now;
		updatedAt = now;
		if (nextAttemptAt == null) {
			nextAttemptAt = now;
		}
	}

	public void claim(String workerId, LocalDateTime now) {
		status = OutboxEventStatus.PROCESSING;
		attemptCount++;
		lockedAt = now;
		lockedBy = workerId;
		updatedAt = now;
	}

	public void markProcessed(LocalDateTime now) {
		status = OutboxEventStatus.PROCESSED;
		processedAt = now;
		lastError = null;
		clearLock();
		updatedAt = now;
	}

	public void scheduleRetry(
			String error,
			LocalDateTime nextAttemptAt,
			LocalDateTime now
	) {
		status = OutboxEventStatus.PENDING;
		lastError = error;
		this.nextAttemptAt = nextAttemptAt;
		clearLock();
		updatedAt = now;
	}

	public void markFailed(String error, LocalDateTime now) {
		status = OutboxEventStatus.FAILED;
		lastError = error;
		clearLock();
		updatedAt = now;
	}

	private void clearLock() {
		lockedAt = null;
		lockedBy = null;
	}
}
