package ru.itmo.nemat.weezzy.team.request.position.dto;

import ru.itmo.nemat.weezzy.skill.dto.SkillResponse;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPosition;

import java.util.List;
import java.util.UUID;

public record TeamRequestPositionResponse(
		UUID id,
		String name,
		String description,
		int slotsCount,
		List<SkillResponse> skills
) {
	public static TeamRequestPositionResponse from(
			TeamRequestPosition position, List<SkillResponse> skills
	) {
		return new TeamRequestPositionResponse(
				position.getId(),
				position.getName(),
				position.getDescription(),
				position.getSlotsCount(),
				List.copyOf(skills)
		);
	}
}
