package ru.itmo.nemat.weezzy.interest.dto;

import ru.itmo.nemat.weezzy.interest.Interest;

import java.time.LocalDateTime;
import java.util.UUID;

public record InterestResponse(
		UUID id,
		String name,
		String description,
		LocalDateTime createdAt
) {
	public static InterestResponse from(Interest interest) {
		return new InterestResponse(
				interest.getId(),
				interest.getName(),
				interest.getDescription(),
				interest.getCreatedAt()
		);
	}
}
