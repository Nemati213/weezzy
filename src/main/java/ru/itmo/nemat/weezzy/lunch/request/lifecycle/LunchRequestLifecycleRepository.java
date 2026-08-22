package ru.itmo.nemat.weezzy.lunch.request.lifecycle;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface LunchRequestLifecycleRepository
		extends Repository<LunchRequest, UUID> {
	@Query(value = """
			SELECT lunch_request.*
			FROM lunch_requests lunch_request
			WHERE lunch_request.status = 'SEARCHING'
			  AND lunch_request.time_slot <= :now
			  AND lunch_request.time_slot > :minimumTimeSlot
			  AND CAST(lunch_request.time_slot AS date) = CAST(:now AS date)
			  AND lunch_request.extension_count < :maxExtensions
			  AND CAST(lunch_request.time_slot AS time) <= :latestTimeSlot
			ORDER BY lunch_request.time_slot, lunch_request.id
			LIMIT :batchSize
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<LunchRequest> findDueForUpdate(
			@Param("now") LocalDateTime now,
			@Param("minimumTimeSlot") LocalDateTime minimumTimeSlot,
			@Param("latestTimeSlot") LocalTime latestTimeSlot,
			@Param("maxExtensions") int maxExtensions,
			@Param("batchSize") int batchSize
	);

	@Query(value = """
			SELECT lunch_request.*
			FROM lunch_requests lunch_request
			WHERE (
				lunch_request.status = 'EXTENSION_REQUESTED'
				AND (
					lunch_request.extension_expires_at IS NULL
					OR lunch_request.extension_target_time_slot IS NULL
					OR lunch_request.extension_expires_at <= :now
					OR lunch_request.extension_target_time_slot <= :now
				)
			) OR (
				lunch_request.status = 'SEARCHING'
				AND lunch_request.time_slot <= :now
				AND (
					CAST(lunch_request.time_slot AS date) < CAST(:now AS date)
					OR lunch_request.time_slot <= :minimumTimeSlot
					OR lunch_request.extension_count >= :maxExtensions
					OR CAST(lunch_request.time_slot AS time) > :latestTimeSlot
				)
			)
			ORDER BY COALESCE(
				lunch_request.extension_expires_at,
				lunch_request.time_slot
			), lunch_request.id
			LIMIT :batchSize
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<LunchRequest> findExpirableForUpdate(
			@Param("now") LocalDateTime now,
			@Param("minimumTimeSlot") LocalDateTime minimumTimeSlot,
			@Param("latestTimeSlot") LocalTime latestTimeSlot,
			@Param("maxExtensions") int maxExtensions,
			@Param("batchSize") int batchSize
	);
}
