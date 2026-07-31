package ru.itmo.nemat.weezzy.interest;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InterestRepository extends JpaRepository<Interest, UUID> {
	Optional<Interest> findByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCase(String name);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT interest FROM Interest interest WHERE interest.id = :id")
	Optional<Interest> findByIdForUpdate(@Param("id") UUID id);
}
