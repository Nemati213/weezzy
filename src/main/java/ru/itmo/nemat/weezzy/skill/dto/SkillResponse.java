package ru.itmo.nemat.weezzy.skill.dto;

import ru.itmo.nemat.weezzy.skill.Skill;

import java.time.LocalDateTime;
import java.util.UUID;

public record SkillResponse(
		UUID id,
		String name,
		String description,
		LocalDateTime createdAt
) {
	public static SkillResponse from(Skill skill) {
		return new SkillResponse(
				skill.getId(),
				skill.getName(),
				skill.getDescription(),
				skill.getCreatedAt()
		);
	}
}
