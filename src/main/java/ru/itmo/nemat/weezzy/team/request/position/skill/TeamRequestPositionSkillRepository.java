package ru.itmo.nemat.weezzy.team.request.position.skill;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TeamRequestPositionSkillRepository
		extends JpaRepository<TeamRequestPositionSkill, TeamRequestPositionSkillId> {
	@EntityGraph(attributePaths = {"skill"})
	List<TeamRequestPositionSkill> findByPositionIdOrderBySkillIdAsc(UUID positionId);

	@EntityGraph(attributePaths = {"skill"})
	List<TeamRequestPositionSkill> findByPositionIdInOrderByPositionIdAscSkillIdAsc(
			Collection<UUID> positionIds
	);
}
