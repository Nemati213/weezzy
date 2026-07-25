package ru.itmo.nemat.weezzy.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "goals")
@Data
public class Goal {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotBlank
	@Size(max = 60)
	@Pattern(regexp = "[A-Z][A-Z0-9_]*")
	@Column(nullable = false, length = 60)
	private String code;

	@NotBlank
	@Size(max = 100)
	@Column(nullable = false, length = 100)
	private String name;

	@Size(max = 500)
	@Column(length = 500)
	private String description;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
