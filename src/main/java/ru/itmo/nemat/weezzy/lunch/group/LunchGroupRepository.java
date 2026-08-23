package ru.itmo.nemat.weezzy.lunch.group;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LunchGroupRepository extends JpaRepository<LunchGroup, UUID> {
	@EntityGraph(attributePaths = "location")
	Optional<LunchGroup> findByIdAndStatus(UUID id, LunchGroupStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT lunchGroup FROM LunchGroup lunchGroup WHERE lunchGroup.id = :id")
	Optional<LunchGroup> findByIdForUpdate(@Param("id") UUID id);

}
