package ru.itmo.nemat.weezzy.team.request.position.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateRecruitmentPositionRequest(
		@NotBlank @Size(max = 120) String name,
		@Size(max = 2000) String description,
		@Positive int slotsCount,
		@NotNull Set<@NotNull UUID> skillIds
) {
}
