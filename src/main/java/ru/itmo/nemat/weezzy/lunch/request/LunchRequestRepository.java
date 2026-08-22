package ru.itmo.nemat.weezzy.lunch.request;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LunchRequestRepository extends JpaRepository<LunchRequest, UUID> {
	boolean existsByProfileIdAndStatusIn(
			UUID profileId,
			Collection<LunchRequestStatus> statuses
	);

	boolean existsByProfileIdAndStatusAndTimeSlotGreaterThanEqualAndTimeSlotLessThan(
			UUID profileId,
			LunchRequestStatus status,
			LocalDateTime dayStart,
			LocalDateTime nextDayStart
	);

	@EntityGraph(attributePaths = {"profile", "location", "location.university"})
	Optional<LunchRequest> findFirstByProfileIdAndStatusInOrderByCreatedAtDesc(
			UUID profileId,
			Collection<LunchRequestStatus> statuses
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = {"profile", "location", "location.university"})
	@Query("""
			SELECT request
			FROM LunchRequest request
			WHERE request.profile.id = :profileId
			  AND request.status IN :statuses
			""")
	Optional<LunchRequest> findActiveForUpdate(
			@Param("profileId") UUID profileId,
			@Param("statuses") Collection<LunchRequestStatus> statuses
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = {"profile", "location", "location.university"})
	Optional<LunchRequest> findFirstByProfileIdOrderByCreatedAtDesc(UUID profileId);

	@Query("""
			SELECT request.profile.id
			FROM LunchRequest request
			WHERE request.id IN :requestIds
			ORDER BY request.profile.id
			""")
	List<UUID> findProfileIdsByRequestIds(
			@Param("requestIds") Collection<UUID> requestIds
	);

	@Query("""
			SELECT request.profile.user.id
			FROM LunchRequest request
			WHERE request.id IN :requestIds
			ORDER BY request.profile.user.id
			""")
	List<UUID> findOwnerUserIdsByRequestIds(
			@Param("requestIds") Collection<UUID> requestIds
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT request
			FROM LunchRequest request
			WHERE request.id IN :requestIds
			ORDER BY request.id
			""")
	List<LunchRequest> findAllByIdForUpdate(
			@Param("requestIds") Collection<UUID> requestIds
	);

	@EntityGraph(attributePaths = {"profile", "profile.user"})
	@Query("""
			SELECT request
			FROM LunchRequest request
			WHERE request.id = :requestId
			""")
	Optional<LunchRequest> findForExtensionNotification(
			@Param("requestId") UUID requestId
	);
}
