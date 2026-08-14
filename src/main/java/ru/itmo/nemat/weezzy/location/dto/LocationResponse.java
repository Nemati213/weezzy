package ru.itmo.nemat.weezzy.location.dto;

import ru.itmo.nemat.weezzy.location.Location;
import ru.itmo.nemat.weezzy.location.LocationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record LocationResponse(
		UUID id,
		UniversityResponse university,
		LocationType type,
		String name,
		String address,
		String description,
		boolean active,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static LocationResponse from(Location location) {
		return new LocationResponse(
				location.getId(),
				UniversityResponse.from(location.getUniversity()),
				location.getType(),
				location.getName(),
				location.getAddress(),
				location.getDescription(),
				location.isActive(),
				location.getCreatedAt(),
				location.getUpdatedAt()
		);
	}
}
