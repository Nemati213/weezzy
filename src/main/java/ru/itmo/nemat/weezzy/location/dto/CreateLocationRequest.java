package ru.itmo.nemat.weezzy.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.itmo.nemat.weezzy.location.LocationType;

import java.util.UUID;

public record CreateLocationRequest(
		@NotNull
		UUID universityId,

		@NotNull
		LocationType type,

		@NotBlank
		@Size(max = 255)
		String name,

		@NotBlank
		@Size(max = 255)
		String address,

		@Size(max = 2000)
		String description
) {
}
