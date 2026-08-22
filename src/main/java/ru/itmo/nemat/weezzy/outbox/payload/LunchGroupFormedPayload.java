package ru.itmo.nemat.weezzy.outbox.payload;

import jakarta.validation.constraints.NotNull;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

import java.util.UUID;

public record LunchGroupFormedPayload(
		@NotNull UUID groupId
) implements OutboxPayload {
	@Override
	public OutboxEventType eventType() {
		return OutboxEventType.LUNCH_GROUP_FORMED;
	}
}
