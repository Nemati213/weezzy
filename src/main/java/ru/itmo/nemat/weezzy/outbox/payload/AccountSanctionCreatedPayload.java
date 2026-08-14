package ru.itmo.nemat.weezzy.outbox.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccountSanctionCreatedPayload(
		@NotNull UUID sanctionId,
		@NotNull UUID recipientUserId,
		@NotNull AccountSanctionType type,
		@NotBlank @Size(max = 1000) String reason,
		LocalDateTime expiresAt,
		UUID sourceReportId
) implements OutboxPayload {
	@Override
	public OutboxEventType eventType() {
		return OutboxEventType.ACCOUNT_SANCTION_CREATED;
	}
}
