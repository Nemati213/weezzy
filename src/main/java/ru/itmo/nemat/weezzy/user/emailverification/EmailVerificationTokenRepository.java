package ru.itmo.nemat.weezzy.user.emailverification;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository
		extends JpaRepository<EmailVerificationToken, UUID> {

	boolean existsByUserId(UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT token
			FROM EmailVerificationToken token
			JOIN FETCH token.user
			WHERE token.id = :tokenId
			""")
	Optional<EmailVerificationToken> findByIdForUpdate(@Param("tokenId") UUID tokenId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT token
			FROM EmailVerificationToken token
			JOIN FETCH token.user
			WHERE token.user.id = :userId
				AND token.usedAt IS NULL
				AND token.revokedAt IS NULL
			""")
	Optional<EmailVerificationToken> findActiveByUserIdForUpdate(
			@Param("userId") UUID userId
	);
}
