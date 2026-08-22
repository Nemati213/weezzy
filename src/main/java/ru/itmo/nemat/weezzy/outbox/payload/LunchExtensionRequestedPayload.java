package ru.itmo.nemat.weezzy.outbox.payload;

import jakarta.validation.constraints.NotNull;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

import java.util.UUID;

public record LunchExtensionRequestedPayload(
		@NotNull UUID requestId,
		@NotNull UUID offerId
) implements OutboxPayload {
	@Override
	public OutboxEventType eventType() {
		return OutboxEventType.LUNCH_EXTENSION_REQUESTED;
	}
}
