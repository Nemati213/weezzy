package ru.itmo.nemat.weezzy.location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UniversityRepository extends JpaRepository<University, UUID> {
	Optional<University> findByNameIgnoreCaseAndCityIgnoreCase(String name, String city);
}
