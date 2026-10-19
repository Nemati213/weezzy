package ru.itmo.nemat.weezzy.team.position.skill;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TeamPositionSkillRepository
		extends JpaRepository<TeamPositionSkill, TeamPositionSkillId> {
	@EntityGraph(attributePaths = {"skill"})
	List<TeamPositionSkill> findByPositionIdOrderBySkillIdAsc(UUID positionId);

	@EntityGraph(attributePaths = {"skill"})
	List<TeamPositionSkill> findByPositionIdInOrderByPositionIdAscSkillIdAsc(
			Collection<UUID> positionIds
	);
}
