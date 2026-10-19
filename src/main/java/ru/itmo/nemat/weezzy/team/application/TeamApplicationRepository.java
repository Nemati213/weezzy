package ru.itmo.nemat.weezzy.team.application;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TeamApplicationRepository extends JpaRepository<TeamApplication, UUID> {
	boolean existsByPositionIdAndApplicantProfileIdAndStatus(
			UUID positionId, UUID applicantProfileId, TeamApplicationStatus status
	);

	@Query("""
			SELECT new ru.itmo.nemat.weezzy.team.application.TeamApplicationParticipants(
			    application.position.request.id,
			    application.position.request.team.ownerProfile.id,
			    application.applicantProfile.id)
			FROM TeamApplication application WHERE application.id = :id
			""")
	Optional<TeamApplicationParticipants> findParticipants(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT application FROM TeamApplication application WHERE application.id = :id")
	Optional<TeamApplication> findByIdForUpdate(@Param("id") UUID id);

	@EntityGraph(attributePaths = {"position", "position.request", "position.request.team"})
	@Query("""
			SELECT application FROM TeamApplication application
			WHERE application.applicantProfile.id = :profileId
			  AND (:status IS NULL OR application.status = :status)
			""")
	Page<TeamApplication> findOwn(
			@Param("profileId") UUID profileId,
			@Param("status") TeamApplicationStatus status,
			Pageable pageable
	);

	@EntityGraph(attributePaths = {"position", "position.request", "position.request.team"})
	@Query("""
			SELECT application FROM TeamApplication application
			WHERE application.position.request.id = :requestId
			  AND (:status IS NULL OR application.status = :status)
			  AND (:positionId IS NULL OR application.position.id = :positionId)
			""")
	Page<TeamApplication> findForRequest(
			@Param("requestId") UUID requestId,
			@Param("positionId") UUID positionId,
			@Param("status") TeamApplicationStatus status,
			Pageable pageable
	);
}
