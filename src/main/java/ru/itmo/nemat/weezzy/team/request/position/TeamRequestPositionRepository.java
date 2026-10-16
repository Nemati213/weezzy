package ru.itmo.nemat.weezzy.team.request.position;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TeamRequestPositionRepository
		extends JpaRepository<TeamRequestPosition, UUID> {
	List<TeamRequestPosition> findByRequestIdOrderByCreatedAtAscIdAsc(UUID requestId);

	List<TeamRequestPosition> findByRequestIdInOrderByCreatedAtAscIdAsc(
			Collection<UUID> requestIds
	);

	long countByRequestId(UUID requestId);
}
