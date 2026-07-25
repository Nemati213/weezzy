package ru.itmo.nemat.weezzy.goal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateGoalRequest(
		@NotBlank
		@Size(max = 60)
		@Pattern(regexp = "[A-Z][A-Z0-9_]*")
		String code,

		@NotBlank
		@Size(max = 100)
		String name,

		@Size(max = 500)
		String description
) {
}
