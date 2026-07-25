package ru.itmo.nemat.weezzy.interest.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateInterestRequest(
		@Size(max = 80)
		@Pattern(regexp = ".*\\S.*")
		String name,

		@Size(max = 500)
		String description
) {
}
