package ru.itmo.nemat.weezzy.profile.interest;

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
@Table(name = "profile_interests")
@IdClass(ProfileInterestId.class)
@Data
public class ProfileInterest {
	@Id
	@Column(nullable = false)
	private UUID profileId;

	@Id
	@Column(nullable = false)
	private UUID interestId;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
