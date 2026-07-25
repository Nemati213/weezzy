package ru.itmo.nemat.weezzy.goal.dto;

import ru.itmo.nemat.weezzy.goal.Goal;

import java.time.LocalDateTime;
import java.util.UUID;

public record GoalResponse(
		UUID id,
		String code,
		String name,
		String description,
		LocalDateTime createdAt
) {
	public static GoalResponse from(Goal goal) {
		return new GoalResponse(
				goal.getId(),
				goal.getCode(),
				goal.getName(),
				goal.getDescription(),
				goal.getCreatedAt()
		);
	}
}
