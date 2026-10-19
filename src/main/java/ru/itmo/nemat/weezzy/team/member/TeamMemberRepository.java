package ru.itmo.nemat.weezzy.team.member;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {
	@EntityGraph(attributePaths = {"profile", "profile.user", "position"})
	List<TeamMember> findByTeamIdOrderByJoinedAtAscProfileIdAsc(UUID teamId);

	long countByTeamId(UUID teamId);

	long countByPositionId(UUID positionId);

	@Query("""
			SELECT new ru.itmo.nemat.weezzy.team.member.TeamMemberCount(
			    member.position.id, COUNT(member))
			FROM TeamMember member WHERE member.position.id IN :ids
			GROUP BY member.position.id
			""")
	List<TeamMemberCount> countByPositions(@Param("ids") Collection<UUID> ids);

	@Query("""
			SELECT new ru.itmo.nemat.weezzy.team.member.TeamMemberCount(
			    member.team.id, COUNT(member))
			FROM TeamMember member WHERE member.team.id IN :ids
			GROUP BY member.team.id
			""")
	List<TeamMemberCount> countByTeams(@Param("ids") Collection<UUID> ids);
}
