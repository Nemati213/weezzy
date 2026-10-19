package ru.itmo.nemat.weezzy.team.position;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TeamPositionRepository extends JpaRepository<TeamPosition, UUID> {
	List<TeamPosition> findByTeamIdOrderByCreatedAtAscIdAsc(UUID teamId);

	List<TeamPosition> findByIdInAndTeamId(Collection<UUID> ids, UUID teamId);
}
