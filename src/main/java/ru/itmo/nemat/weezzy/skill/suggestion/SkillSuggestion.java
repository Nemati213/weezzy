package ru.itmo.nemat.weezzy.skill.suggestion;

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
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ru.itmo.nemat.weezzy.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "skill_suggestions")
@Data
public class SkillSuggestion {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "suggested_by_user_id", nullable = false)
	private User suggestedBy;

	@NotBlank
	@Size(max = 80)
	@Column(nullable = false, length = 80)
	private String name;

	@Size(max = 500)
	@Column(length = 500)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private SkillSuggestionStatus status = SkillSuggestionStatus.PENDING;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime reviewedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reviewed_by_user_id")
	private User reviewedBy;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}

}
