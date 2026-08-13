package ru.itmo.nemat.weezzy.profile;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
	List<Profile> findAllByStatusNot(ProfileStatus status);

	Page<Profile> findAllByStatusNot(ProfileStatus status, Pageable pageable);

	@Query("""
			SELECT profile
			FROM Profile profile
			WHERE profile.status <> 'DELETED'
				AND NOT EXISTS (
					SELECT sanction.id
					FROM AccountSanction sanction
					WHERE sanction.targetUserId = profile.user.id
						AND sanction.status = 'ACTIVE'
						AND (
							sanction.type = 'PERMANENT_BAN'
							OR sanction.expiresAt > :now
						)
				)
			""")
	Page<Profile> findAllVisible(@Param("now") LocalDateTime now, Pageable pageable);

	@Query("""
			SELECT profile
			FROM Profile profile
			WHERE profile.status <> 'DELETED'
				AND NOT EXISTS (
					SELECT sanction.id
					FROM AccountSanction sanction
					WHERE sanction.targetUserId = profile.user.id
						AND sanction.status = 'ACTIVE'
						AND (
							sanction.type = 'PERMANENT_BAN'
							OR sanction.expiresAt > :now
						)
				)
			""")
	List<Profile> findAllVisible(@Param("now") LocalDateTime now);

	boolean existsByUserId(UUID userId);
	Optional<Profile> findByUserId(UUID userId);

	@Query("SELECT profile.user.id FROM Profile profile WHERE profile.id = :profileId")
	Optional<UUID> findOwnerUserId(@Param("profileId") UUID profileId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT profile
			FROM Profile profile
			WHERE profile.user.id = :userId
			""")
	Optional<Profile> findByUserIdForUpdate(@Param("userId") UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT profile
			FROM Profile profile
			WHERE profile.id IN :profileIds
			ORDER BY profile.id
			""")
	List<Profile> findAllByIdForUpdate(
			@Param("profileIds") Collection<UUID> profileIds
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT profile FROM Profile profile WHERE profile.id = :id")
	Optional<Profile> findByIdForUpdate(@Param("id") UUID id);
}
