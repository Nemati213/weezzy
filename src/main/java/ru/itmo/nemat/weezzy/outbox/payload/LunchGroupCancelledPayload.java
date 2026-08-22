package ru.itmo.nemat.weezzy.outbox.payload;

import jakarta.validation.constraints.NotNull;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupCancellationReason;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

import java.util.UUID;

public record LunchGroupCancelledPayload(
		@NotNull UUID groupId,
		@NotNull LunchGroupCancellationReason reason
) implements OutboxPayload {
	@Override
	public OutboxEventType eventType() {
		return OutboxEventType.LUNCH_GROUP_CANCELLED;
	}
}
