package ru.itmo.nemat.weezzy.connection.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "profile_interaction_events")
@Data
public class ProfileInteractionEvent {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID sourceProfileId;

	@Column(nullable = false, updatable = false)
	private UUID targetProfileId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 40)
	private ProfileInteractionEventType eventType;

	@Column(nullable = false, updatable = false)
	private LocalDateTime occurredAt;

	@PrePersist
	void onCreate() {
		occurredAt = LocalDateTime.now();
	}
}
