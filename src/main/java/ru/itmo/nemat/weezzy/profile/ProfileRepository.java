package ru.itmo.nemat.weezzy.profile;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
	boolean existsByUserId(UUID userId);
	Optional<Profile> findByUserId(UUID userId);

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
