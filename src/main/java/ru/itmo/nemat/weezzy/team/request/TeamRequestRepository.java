package ru.itmo.nemat.weezzy.team.request;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TeamRequestRepository extends JpaRepository<TeamRequest, UUID> {
	@EntityGraph(attributePaths = {"team"})
	Page<TeamRequest> findByTeamId(UUID teamId, Pageable pageable);

	@EntityGraph(attributePaths = {"team"})
	Page<TeamRequest> findByTeamIdAndStatus(
			UUID teamId, TeamRequestStatus status, Pageable pageable
	);

	@Query("SELECT request.team.id FROM TeamRequest request WHERE request.id = :id")
	Optional<UUID> findTeamId(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT request FROM TeamRequest request WHERE request.id = :id")
	Optional<TeamRequest> findByIdForUpdate(@Param("id") UUID id);
}
