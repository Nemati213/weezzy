package ru.itmo.nemat.weezzy.location.dto;

import ru.itmo.nemat.weezzy.location.University;

import java.time.LocalDateTime;
import java.util.UUID;

public record UniversityResponse(
		UUID id,
		String name,
		String city,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static UniversityResponse from(University university) {
		return new UniversityResponse(
				university.getId(),
				university.getName(),
				university.getCity(),
				university.getCreatedAt(),
				university.getUpdatedAt()
		);
	}
}
