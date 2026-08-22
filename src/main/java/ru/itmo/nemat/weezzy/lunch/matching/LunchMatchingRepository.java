package ru.itmo.nemat.weezzy.lunch.matching;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LunchMatchingRepository extends Repository<LunchRequest, UUID> {
	@Query("""
			SELECT new ru.itmo.nemat.weezzy.lunch.matching.MatchingBucketKey(
				request.location.id,
				request.timeSlot
			)
			FROM LunchRequest request
			WHERE request.status = 'SEARCHING'
			  AND request.timeSlot > :now
			GROUP BY request.location.id, request.timeSlot
			ORDER BY request.timeSlot, request.location.id
			""")
	List<MatchingBucketKey> findBucketKeys(
			@Param("now") LocalDateTime now,
			Pageable pageable
	);

	@Query(value = "SELECT pg_try_advisory_xact_lock(:lockKey)", nativeQuery = true)
	boolean tryClaimBucket(@Param("lockKey") long lockKey);

	@Query("""
			SELECT request.profile.user.id
			FROM LunchRequest request
			WHERE request.status = 'SEARCHING'
			  AND request.location.id = :locationId
			  AND request.timeSlot = :timeSlot
			ORDER BY request.profile.user.id
			""")
	List<UUID> findOwnerUserIds(
			@Param("locationId") UUID locationId,
			@Param("timeSlot") LocalDateTime timeSlot
	);

	@Query("""
			SELECT request.profile.id
			FROM LunchRequest request
			WHERE request.status = 'SEARCHING'
			  AND request.location.id = :locationId
			  AND request.timeSlot = :timeSlot
			ORDER BY request.profile.id
			""")
	List<UUID> findProfileIds(
			@Param("locationId") UUID locationId,
			@Param("timeSlot") LocalDateTime timeSlot
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT request
			FROM LunchRequest request
			WHERE request.status = 'SEARCHING'
			  AND request.location.id = :locationId
			  AND request.timeSlot = :timeSlot
			ORDER BY request.id
			""")
	List<LunchRequest> findRequestsForUpdate(
			@Param("locationId") UUID locationId,
			@Param("timeSlot") LocalDateTime timeSlot
	);
}
