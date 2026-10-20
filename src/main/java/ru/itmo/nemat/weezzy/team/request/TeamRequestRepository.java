package ru.itmo.nemat.weezzy.team.request;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itmo.nemat.weezzy.team.TeamVisibility;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TeamRequestRepository extends JpaRepository<TeamRequest, UUID> {
	@EntityGraph(attributePaths = {"team"})
	@Query("""
			SELECT request FROM TeamRequest request JOIN request.team team
			WHERE request.status = 'OPEN' AND request.recruitmentDeadline > :now
			AND
			""" + TeamVisibility.CONDITION + """
			AND NOT EXISTS (
			    SELECT member.id FROM TeamMember member
			    WHERE member.team.id = team.id AND member.profile.id = :viewerProfileId
			)
			AND EXISTS (
			    SELECT link.id FROM TeamRequestPosition link
			    JOIN link.position position
			    WHERE link.request.id = request.id
			      AND position.capacity > (
			          SELECT COUNT(member) FROM TeamMember member
			          WHERE member.position.id = position.id
			      )
			      AND (:skillCount = 0 OR :skillCount = (
			          SELECT COUNT(skill) FROM TeamPositionSkill skill
			          WHERE skill.position.id = position.id AND skill.skill.id IN :skillIds
			      ))
			      AND (LOWER(request.title) LIKE :pattern ESCAPE '!'
			          OR LOWER(request.description) LIKE :pattern ESCAPE '!'
			          OR LOWER(team.name) LIKE :pattern ESCAPE '!'
			          OR LOWER(team.purpose) LIKE :pattern ESCAPE '!'
			          OR LOWER(position.name) LIKE :pattern ESCAPE '!'
			          OR LOWER(position.description) LIKE :pattern ESCAPE '!')
			)
			""")
	Page<TeamRequest> search(
			@Param("viewerProfileId") UUID viewerProfileId,
			@Param("now") LocalDateTime now, @Param("pattern") String pattern,
			@Param("skillIds") Set<UUID> skillIds, @Param("skillCount") int skillCount,
			Pageable pageable
	);

	@EntityGraph(attributePaths = {"team"})
	Page<TeamRequest> findByTeamId(UUID teamId, Pageable pageable);

	@EntityGraph(attributePaths = {"team"})
	Page<TeamRequest> findByTeamIdAndStatus(
			UUID teamId, TeamRequestStatus status, Pageable pageable
	);

	@Query("SELECT request.team.id FROM TeamRequest request WHERE request.id = :id")
	Optional<UUID> findTeamId(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT request FROM TeamRequest request WHERE request.id = :id")
	Optional<TeamRequest> findByIdForUpdate(@Param("id") UUID id);
}
