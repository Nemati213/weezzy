package ru.itmo.nemat.weezzy.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
	Optional<Goal> findByCodeIgnoreCase(String code);

	Optional<Goal> findByNameIgnoreCase(String name);
}
