package ru.itmo.nemat.weezzy.team.position.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record UpdateTeamPositionRequest(
		@NotBlank @Size(max = 120) String name,
		@Size(max = 2000) String description,
		@Positive int capacity,
		@NotNull Set<@NotNull UUID> skillIds
) {
}
