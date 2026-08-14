package ru.itmo.nemat.weezzy.outbox.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.itmo.nemat.weezzy.moderation.report.ProfileReportStatus;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

import java.util.UUID;

public record ProfileReportDecidedPayload(
		@NotNull UUID reportId,
		@NotNull UUID recipientUserId,
		@NotNull UUID targetProfileId,
		@NotNull ProfileReportStatus status,
		@NotBlank @Size(max = 1000) String decision
) implements OutboxPayload {
	@Override
	public OutboxEventType eventType() {
		return OutboxEventType.REPORT_DECIDED;
	}
}
