package ru.itmo.nemat.weezzy.outbox.payload;

import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

import java.util.UUID;

public record ProfileLikedPayload(
		UUID sourceProfileId,
		UUID targetProfileId,
		UUID recipientUserId
) implements OutboxPayload {
	@Override
	public OutboxEventType eventType() {
		return OutboxEventType.PROFILE_LIKED;
	}
}
