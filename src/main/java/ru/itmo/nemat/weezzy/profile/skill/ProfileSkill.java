package ru.itmo.nemat.weezzy.profile.skill;

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
@Table(name = "profile_skills")
@IdClass(ProfileSkillId.class)
@Data
public class ProfileSkill {
	@Id
	@Column(nullable = false)
	private UUID profileId;

	@Id
	@Column(nullable = false)
	private UUID skillId;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
