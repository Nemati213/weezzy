package ru.itmo.nemat.weezzy.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
	@Query(value = """
			SELECT event.*
			FROM outbox_events event
			WHERE event.status = 'PENDING'
				AND event.next_attempt_at <= :now
			ORDER BY event.next_attempt_at, event.created_at, event.id
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<OutboxEvent> findPendingForUpdate(
			@Param("now") LocalDateTime now,
			@Param("limit") int limit
	);

	@Query(value = """
			SELECT event.*
			FROM outbox_events event
			WHERE event.status = 'PROCESSING'
				AND event.locked_at < :lockedBefore
			ORDER BY event.locked_at, event.id
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<OutboxEvent> findStaleProcessingForUpdate(
			@Param("lockedBefore") LocalDateTime lockedBefore,
			@Param("limit") int limit
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT event FROM OutboxEvent event WHERE event.id = :eventId")
	Optional<OutboxEvent> findByIdForUpdate(@Param("eventId") UUID eventId);

	long countByStatus(OutboxEventStatus status);

	@Modifying
	@Query(value = """
			DELETE FROM outbox_events
			WHERE id IN (
				SELECT event.id
				FROM outbox_events event
				WHERE event.status = 'PROCESSED'
					AND event.processed_at < :processedBefore
				ORDER BY event.processed_at, event.id
				LIMIT :limit
				FOR UPDATE SKIP LOCKED
			)
			""", nativeQuery = true)
	int deleteProcessedBefore(
			@Param("processedBefore") LocalDateTime processedBefore,
			@Param("limit") int limit
	);
}
