package ru.itmo.nemat.weezzy.team;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
	@EntityGraph(attributePaths = {"ownerProfile", "ownerProfile.user"})
	Optional<Team> findByIdAndStatus(UUID id, TeamStatus status);

	@Query("SELECT team.ownerProfile.id FROM Team team WHERE team.id = :teamId")
	Optional<UUID> findOwnerProfileId(@Param("teamId") UUID teamId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT team FROM Team team WHERE team.id = :teamId")
	Optional<Team> findByIdForUpdate(@Param("teamId") UUID teamId);
}
