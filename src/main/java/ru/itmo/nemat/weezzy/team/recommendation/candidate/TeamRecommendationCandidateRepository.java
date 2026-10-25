package ru.itmo.nemat.weezzy.team.recommendation.candidate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ru.itmo.nemat.weezzy.team.TeamVisibility;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPosition;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TeamRecommendationCandidateRepository
		extends Repository<TeamRequestPosition, UUID> {
	String ELIGIBLE_FROM = """
			FROM TeamRequestPosition link
			JOIN link.request request JOIN request.team team JOIN link.position position
			WHERE request.status = 'OPEN' AND request.recruitmentDeadline > :now
			AND position.team.id = team.id
			AND
			""" + TeamVisibility.CONDITION + """
			AND NOT EXISTS (
			    SELECT member.id FROM TeamMember member
			    WHERE member.team.id = team.id AND member.profile.id = :viewerProfileId
			)
			AND position.capacity > (
			    SELECT COUNT(member) FROM TeamMember member
			    WHERE member.position.id = position.id
			)
			AND NOT EXISTS (
			    SELECT application.id FROM TeamApplication application
			    WHERE application.applicantProfile.id = :viewerProfileId
			      AND application.position.position.id = position.id
			      AND (application.status = 'PENDING'
			          OR (application.status = 'DECLINED'
			              AND application.resolvedAt > :declinedAfter))
			)
			""";

	@Query("""
			SELECT new ru.itmo.nemat.weezzy.team.recommendation.candidate.TeamRecommendationCandidate(
			    team.id, request.id, request.createdAt, request.recruitmentDeadline,
			    link.id, position.id, position.createdAt)
			""" + ELIGIBLE_FROM + """
			AND (:afterRequestPositionId IS NULL OR link.id > :afterRequestPositionId)
			ORDER BY link.id ASC
			""")
	List<TeamRecommendationCandidate> findEligible(
			@Param("viewerProfileId") UUID viewerProfileId,
			@Param("now") LocalDateTime now,
			@Param("declinedAfter") LocalDateTime declinedAfter,
			@Param("afterRequestPositionId") UUID afterRequestPositionId,
			Pageable pageable
	);

	@EntityGraph(attributePaths = {"position", "request", "request.team",
			"request.team.ownerProfile"})
	@Query("SELECT link " + ELIGIBLE_FROM + " AND team.id IN :teamIds ORDER BY link.id ASC")
	List<TeamRequestPosition> findEligibleForTeams(
			@Param("viewerProfileId") UUID viewerProfileId,
			@Param("now") LocalDateTime now,
			@Param("declinedAfter") LocalDateTime declinedAfter,
			@Param("teamIds") Collection<UUID> teamIds
	);

	@Query("SELECT DISTINCT team.id " + ELIGIBLE_FROM + " AND team.id IN :teamIds")
	List<UUID> findEligibleTeamIds(
			@Param("viewerProfileId") UUID viewerProfileId,
			@Param("now") LocalDateTime now,
			@Param("declinedAfter") LocalDateTime declinedAfter,
			@Param("teamIds") Collection<UUID> teamIds
	);
}
