package ru.itmo.nemat.weezzy.goal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
	Optional<Goal> findByCodeIgnoreCase(String code);

	Optional<Goal> findByNameIgnoreCase(String name);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT goal FROM Goal goal WHERE goal.id = :id")
	Optional<Goal> findByIdForUpdate(@Param("id") UUID id);
}
