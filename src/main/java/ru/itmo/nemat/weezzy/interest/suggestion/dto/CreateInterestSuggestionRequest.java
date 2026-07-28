package ru.itmo.nemat.weezzy.interest.suggestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInterestSuggestionRequest(
		@NotBlank
		@Size(max = 80)
		String name,

		@Size(max = 500)
		String description
) {
}
