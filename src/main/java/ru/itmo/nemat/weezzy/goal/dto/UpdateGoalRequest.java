package ru.itmo.nemat.weezzy.goal.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateGoalRequest(
		@Size(max = 60)
		@Pattern(regexp = "[A-Z][A-Z0-9_]*")
		String code,

		@Size(max = 100)
		@Pattern(regexp = ".*\\S.*")
		String name,

		@Size(max = 500)
		String description
) {
}
