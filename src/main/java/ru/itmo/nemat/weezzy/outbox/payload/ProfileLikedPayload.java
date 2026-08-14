package ru.itmo.nemat.weezzy.outbox.payload;

import jakarta.validation.constraints.NotNull;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

import java.util.UUID;

public record ProfileLikedPayload(
		@NotNull UUID sourceProfileId,
		@NotNull UUID targetProfileId,
		@NotNull UUID recipientUserId
) implements OutboxPayload {
	@Override
	public OutboxEventType eventType() {
		return OutboxEventType.PROFILE_LIKED;
	}
}
