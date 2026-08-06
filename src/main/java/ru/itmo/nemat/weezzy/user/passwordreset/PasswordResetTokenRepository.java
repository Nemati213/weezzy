package ru.itmo.nemat.weezzy.user.passwordreset;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository
		extends JpaRepository<PasswordResetToken, UUID> {

	boolean existsByUserId(UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT token
			FROM PasswordResetToken token
			JOIN FETCH token.user
			WHERE token.id = :tokenId
			""")
	Optional<PasswordResetToken> findByIdForUpdate(@Param("tokenId") UUID tokenId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT token
			FROM PasswordResetToken token
			WHERE token.user.id = :userId
				AND token.usedAt IS NULL
				AND token.revokedAt IS NULL
			""")
	Optional<PasswordResetToken> findActiveByUserIdForUpdate(
			@Param("userId") UUID userId
	);
}
