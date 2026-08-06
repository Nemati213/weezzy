package ru.itmo.nemat.weezzy.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT user FROM User user WHERE user.id = :userId")
	Optional<User> findByIdForUpdate(@Param("userId") UUID userId);

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);
}
