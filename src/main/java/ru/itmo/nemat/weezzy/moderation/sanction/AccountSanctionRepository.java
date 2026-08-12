package ru.itmo.nemat.weezzy.moderation.sanction;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AccountSanctionRepository extends JpaRepository<AccountSanction, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT sanction
			FROM AccountSanction sanction
			WHERE sanction.id = :sanctionId
			""")
	Optional<AccountSanction> findByIdForUpdate(@Param("sanctionId") UUID sanctionId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT sanction
			FROM AccountSanction sanction
			WHERE sanction.targetUserId = :targetUserId
				AND sanction.status = :status
			""")
	Optional<AccountSanction> findByTargetUserIdAndStatusForUpdate(
			@Param("targetUserId") UUID targetUserId,
			@Param("status") AccountSanctionStatus status
	);

	@Query("""
			SELECT sanction
			FROM AccountSanction sanction
			WHERE sanction.targetUserId = :targetUserId
				AND sanction.status = 'ACTIVE'
				AND (
					sanction.type = 'PERMANENT_BAN'
					OR sanction.expiresAt > :now
				)
			""")
	Optional<AccountSanction> findEffectiveByTargetUserId(
			@Param("targetUserId") UUID targetUserId,
			@Param("now") LocalDateTime now
	);

	@Query("""
			SELECT DISTINCT sanction.targetUserId
			FROM AccountSanction sanction
			WHERE sanction.targetUserId IN :targetUserIds
				AND sanction.status = 'ACTIVE'
				AND (
					sanction.type = 'PERMANENT_BAN'
					OR sanction.expiresAt > :now
				)
			""")
	Set<UUID> findEffectiveTargetUserIds(
			@Param("targetUserIds") Collection<UUID> targetUserIds,
			@Param("now") LocalDateTime now
	);

	Page<AccountSanction> findByStatusOrderByCreatedAtDescIdDesc(
			AccountSanctionStatus status,
			Pageable pageable
	);

	Page<AccountSanction> findByTargetUserIdOrderByCreatedAtDescIdDesc(
			UUID targetUserId,
			Pageable pageable
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE AccountSanction sanction
			SET sanction.status = 'EXPIRED', sanction.updatedAt = :now
			WHERE sanction.status = 'ACTIVE'
				AND sanction.type = 'TEMPORARY_SUSPENSION'
				AND sanction.expiresAt <= :now
			""")
	int expireAllTemporarySanctions(@Param("now") LocalDateTime now);
}
