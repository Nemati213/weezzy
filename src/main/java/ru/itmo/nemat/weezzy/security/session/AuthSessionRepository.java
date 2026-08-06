package ru.itmo.nemat.weezzy.security.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
	@Modifying
	@Query("""
			UPDATE AuthSession session
			SET session.revokedAt = :revokedAt, session.revokeReason = :reason
			WHERE session.user.id = :userId AND session.revokedAt IS NULL
			""")
	int revokeAllByUserId(
			@Param("userId") UUID userId,
			@Param("revokedAt") LocalDateTime revokedAt,
			@Param("reason") String reason
	);
}
