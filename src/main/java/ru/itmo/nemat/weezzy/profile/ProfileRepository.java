package ru.itmo.nemat.weezzy.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID>, JpaSpecificationExecutor<Profile> {
	boolean existsByUserId(UUID userId);
	Optional<Profile> findByUserId(UUID userId);
}
