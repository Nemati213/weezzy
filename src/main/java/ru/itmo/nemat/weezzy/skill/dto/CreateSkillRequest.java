package ru.itmo.nemat.weezzy.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSkillRequest(
		@NotBlank
		@Size(max = 80)
		String name,

		@Size(max = 500)
		String description
) {
}
