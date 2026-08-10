package ru.itmo.nemat.weezzy.profile.photo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.itmo.nemat.weezzy.profile.Profile;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "profile_photos")
@Getter
@Setter
public class ProfilePhoto {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "profile_id", nullable = false)
	private Profile profile;

	@Column(name = "object_key", nullable = false, unique = true, length = 255)
	private String objectKey;

	@Column(name = "content_type", nullable = false, length = 50)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private Long sizeBytes;

	@Column(nullable = false)
	private Integer position = 0;

	@Column(name = "is_avatar", nullable = false)
	private Boolean isAvatar = false;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProfilePhotoStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "uploaded_at")
	private LocalDateTime uploadedAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
