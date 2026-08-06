package ru.itmo.nemat.weezzy.security.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.itmo.nemat.weezzy.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
@Getter
@Setter
@NoArgsConstructor
public class AuthSession {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime lastUsedAt;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	private LocalDateTime revokedAt;

	@Column(length = 30)
	private String revokeReason;

	@Column(length = 512)
	private String userAgent;

	@Column(length = 45)
	private String ipAddress;
}
