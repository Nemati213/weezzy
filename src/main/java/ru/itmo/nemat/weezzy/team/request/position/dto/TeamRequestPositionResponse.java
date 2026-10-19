package ru.itmo.nemat.weezzy.team.request.position.dto;

import ru.itmo.nemat.weezzy.skill.dto.SkillResponse;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPosition;
import ru.itmo.nemat.weezzy.team.position.dto.TeamPositionResponse;

import java.util.List;
import java.util.UUID;

public record TeamRequestPositionResponse(
		UUID id,
		UUID teamPositionId,
		String name,
		String description,
		int capacity,
		long filledSlotsCount,
		long remainingSlotsCount,
		List<SkillResponse> skills
) {
	public static TeamRequestPositionResponse from(
			TeamRequestPosition link, TeamPositionResponse position
	) {
		return new TeamRequestPositionResponse(
				link.getId(), position.id(), position.name(), position.description(),
				position.capacity(), position.filledSlotsCount(), position.remainingSlotsCount(),
				position.skills()
		);
	}
}
