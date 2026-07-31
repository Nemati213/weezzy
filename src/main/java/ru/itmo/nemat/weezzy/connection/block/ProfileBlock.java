package ru.itmo.nemat.weezzy.connection.block;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;
@Data
@Entity
@Table(name = "profile_blocks")
@IdClass(ProfileBlockId.class)
public class ProfileBlock {
	@Id
	@Column(nullable = false)
	private UUID blockerProfileId;

	@Id
	@Column(nullable = false)
	private UUID blockedProfileId;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
