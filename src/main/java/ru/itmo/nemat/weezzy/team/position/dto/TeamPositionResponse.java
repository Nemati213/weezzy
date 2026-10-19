package ru.itmo.nemat.weezzy.team.position.dto;

import ru.itmo.nemat.weezzy.skill.dto.SkillResponse;
import ru.itmo.nemat.weezzy.team.position.TeamPosition;

import java.util.List;
import java.util.UUID;

public record TeamPositionResponse(
		UUID id, String name, String description, int capacity,
		long filledSlotsCount, long remainingSlotsCount, List<SkillResponse> skills
) {
	public static TeamPositionResponse from(
			TeamPosition position, long filled, List<SkillResponse> skills
	) {
		return new TeamPositionResponse(position.getId(), position.getName(),
				position.getDescription(), position.getCapacity(), filled,
				position.getCapacity() - filled, List.copyOf(skills));
	}
}
