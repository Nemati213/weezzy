package ru.itmo.nemat.weezzy.moderation.sanction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeAccountSanctionRequest(
		@NotBlank
		@Size(max = 1000)
		String reason
) {
}
