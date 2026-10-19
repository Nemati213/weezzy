package ru.itmo.nemat.weezzy.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
		@NotBlank @Size(max = 120) String name,
		@Size(max = 2000) String description,
		@NotBlank @Size(max = 2000) String purpose,
		@NotBlank @Size(max = 120) String ownerPositionName
) {
}
