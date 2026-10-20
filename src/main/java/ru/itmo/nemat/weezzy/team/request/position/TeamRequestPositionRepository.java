package ru.itmo.nemat.weezzy.team.request.position;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRequestPositionRepository
		extends JpaRepository<TeamRequestPosition, UUID> {
	List<TeamRequestPosition> findByRequestIdOrderByCreatedAtAscIdAsc(UUID requestId);

	@EntityGraph(attributePaths = {"position"})
	List<TeamRequestPosition> findByRequestIdInOrderByCreatedAtAscIdAsc(
			Collection<UUID> requestIds
	);

	long countByRequestId(UUID requestId);

	@Query("SELECT position.request.id FROM TeamRequestPosition position WHERE position.id = :id")
	Optional<UUID> findRequestId(@Param("id") UUID id);
}
