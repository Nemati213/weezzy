package ru.itmo.nemat.weezzy.moderation.sanction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateAccountSanctionRequest(
		@NotNull
		AccountSanctionType type,

		@NotBlank
		@Size(max = 1000)
		String reason,

		LocalDateTime expiresAt,

		UUID sourceReportId
) {
}
