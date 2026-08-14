package ru.itmo.nemat.weezzy.outbox.payload;

import jakarta.validation.constraints.NotNull;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

import java.util.UUID;

public record MatchCreatedPayload(
		@NotNull UUID firstProfileId,
		@NotNull UUID firstUserId,
		@NotNull UUID secondProfileId,
		@NotNull UUID secondUserId
) implements OutboxPayload {
	@Override
	public OutboxEventType eventType() {
		return OutboxEventType.MATCH_CREATED;
	}
}
