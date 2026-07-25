package ru.itmo.nemat.weezzy.interest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InterestRepository extends JpaRepository<Interest, UUID> {
	Optional<Interest> findByNameIgnoreCase(String name);
}
