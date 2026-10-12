package ru.itmo.nemat.weezzy.team.invitation;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, UUID> {
	boolean existsByTeamIdAndInviteeProfileIdAndStatus(
			UUID teamId, UUID inviteeProfileId, TeamInvitationStatus status
	);

	@Query("""
			SELECT new ru.itmo.nemat.weezzy.team.invitation.TeamInvitationParticipants(
			    invitation.team.id, invitation.inviterProfile.id,
			    invitation.inviteeProfile.id
			)
			FROM TeamInvitation invitation WHERE invitation.id = :id
			""")
	Optional<TeamInvitationParticipants> findParticipants(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT invitation FROM TeamInvitation invitation WHERE invitation.id = :id")
	Optional<TeamInvitation> findByIdForUpdate(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT invitation FROM TeamInvitation invitation
			WHERE invitation.team.id = :teamId AND invitation.status = 'PENDING'
			  AND (:profileId IS NULL OR invitation.inviteeProfile.id = :profileId)
			ORDER BY invitation.id
			""")
	List<TeamInvitation> findPendingForUpdate(
			@Param("teamId") UUID teamId, @Param("profileId") UUID profileId
	);

	@EntityGraph(attributePaths = {"team", "inviterProfile", "inviteeProfile"})
	@Query("""
			SELECT invitation FROM TeamInvitation invitation
			WHERE invitation.inviteeProfile.user.id = :userId
			  AND invitation.status = :status
			  AND invitation.team.status = 'ACTIVE'
			  AND invitation.team.ownerProfile.id = invitation.inviterProfile.id
			  AND invitation.inviterProfile.status = 'ACTIVE'
			  AND invitation.inviterProfile.user IS NOT NULL
			  AND invitation.inviteeProfile.status = 'ACTIVE'
			  AND NOT EXISTS (
			      SELECT sanction.id FROM AccountSanction sanction
			      WHERE sanction.targetUserId = invitation.inviterProfile.user.id
			        AND sanction.status = 'ACTIVE'
			        AND (sanction.type = 'PERMANENT_BAN' OR sanction.expiresAt > :now)
			  )
			  AND NOT EXISTS (
			      SELECT block.blockerProfileId FROM ProfileBlock block
			      WHERE (block.blockerProfileId = invitation.inviterProfile.id
			             AND block.blockedProfileId = invitation.inviteeProfile.id)
			         OR (block.blockerProfileId = invitation.inviteeProfile.id
			             AND block.blockedProfileId = invitation.inviterProfile.id)
			  )
			""")
	Page<TeamInvitation> findIncoming(
			@Param("userId") UUID userId,
			@Param("status") TeamInvitationStatus status,
			@Param("now") LocalDateTime now,
			Pageable pageable
	);
}
