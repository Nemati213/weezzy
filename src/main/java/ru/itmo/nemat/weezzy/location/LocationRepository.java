package ru.itmo.nemat.weezzy.location;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {
	boolean existsByUniversityIdAndNameIgnoreCaseAndAddressIgnoreCase(
			UUID universityId,
			String name,
			String address
	);

	@EntityGraph(attributePaths = "university")
	Optional<Location> findByIdAndIsActiveTrue(UUID id);

	@EntityGraph(attributePaths = "university")
	@Query("""
			SELECT location
			FROM Location location
			WHERE location.isActive = true
			  AND (:universityId IS NULL OR location.university.id = :universityId)
			  AND (:type IS NULL OR location.type = :type)
			""")
	Page<Location> findActive(
			@Param("universityId") UUID universityId,
			@Param("type") LocationType type,
			Pageable pageable
	);
}
