package ru.itmo.nemat.weezzy.connection.match;

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
@Table(name = "profile_matches")
@Data
@IdClass(ProfileMatchId.class)
public class ProfileMatch {

	@Id
	@Column(nullable = false)
	private UUID firstProfileId;

	@Id
	@Column(nullable = false)
	private UUID secondProfileId;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}

}
