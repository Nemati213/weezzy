package ru.itmo.nemat.weezzy.team.recommendation.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface TeamRecommendationSessionRepository
		extends JpaRepository<TeamRecommendationSession, UUID> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT session FROM TeamRecommendationSession session
			WHERE session.id = :id AND session.ownerUserId = :ownerUserId
			""")
	Optional<TeamRecommendationSession> findOwnedByIdForUpdate(
			@Param("id") UUID id, @Param("ownerUserId") UUID ownerUserId
	);

	Optional<TeamRecommendationSession> findByOwnerUserIdAndClientRequestId(
			UUID ownerUserId, UUID clientRequestId
	);

	@Query("""
			SELECT session FROM TeamRecommendationSession session
			WHERE session.ownerUserId = :ownerUserId AND session.expiresAt > :now
			ORDER BY session.createdAt ASC, session.id ASC
			""")
	List<TeamRecommendationSession> findActiveByOwner(
			@Param("ownerUserId") UUID ownerUserId, @Param("now") LocalDateTime now
	);
}
