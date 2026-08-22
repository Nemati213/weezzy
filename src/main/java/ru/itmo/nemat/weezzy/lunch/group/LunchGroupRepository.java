package ru.itmo.nemat.weezzy.lunch.group;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LunchGroupRepository extends JpaRepository<LunchGroup, UUID> {
	@EntityGraph(attributePaths = "location")
	Optional<LunchGroup> findByIdAndStatus(UUID id, LunchGroupStatus status);
}
