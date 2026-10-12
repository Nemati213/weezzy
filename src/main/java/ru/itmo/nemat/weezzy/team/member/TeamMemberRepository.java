package ru.itmo.nemat.weezzy.team.member;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {
	@EntityGraph(attributePaths = {"profile", "profile.user"})
	List<TeamMember> findByTeamIdOrderByJoinedAtAscProfileIdAsc(UUID teamId);

	long countByTeamId(UUID teamId);
}
