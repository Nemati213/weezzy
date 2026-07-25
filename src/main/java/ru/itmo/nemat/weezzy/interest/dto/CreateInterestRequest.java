package ru.itmo.nemat.weezzy.interest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInterestRequest(
		@NotBlank
		@Size(max = 80)
		String name,

		@Size(max = 500)
		String description
) {
}
