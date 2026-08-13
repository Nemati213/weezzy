package ru.itmo.nemat.weezzy.outbox.payload;

import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

import java.util.UUID;

public record MatchCreatedPayload(
    UUID firstProfileId,
    UUID firstUserId,
    UUID secondProfileId,
    UUID secondUserId
) implements OutboxPayload {
	@Override
	public OutboxEventType eventType() {
		return OutboxEventType.MATCH_CREATED;
	}
}
