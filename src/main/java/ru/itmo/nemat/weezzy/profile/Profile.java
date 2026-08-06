package ru.itmo.nemat.weezzy.profile;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ru.itmo.nemat.weezzy.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "profiles")
@Data
public class Profile {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotBlank
	@Size(max = 80)
	@Column(nullable = false, length = 80)
	private String displayName;

	@Size(max = 500)
	@Column(length = 500)
	private String bio;

	@Size(max = 64)
	@Column(length = 64)
	private String telegram;

	@Size(max = 120)
	@Column(length = 120)
	private String faculty;

	@Size(max = 160)
	@Column(length = 160)
	private String studyProgram;

	@Min(1)
	@Max(6)
	private Integer course;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private LocalDateTime deletedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProfileStatus status = ProfileStatus.DRAFT;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", unique = true)
	private User user;

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
