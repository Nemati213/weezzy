package ru.itmo.nemat.weezzy.outbox.payload;

import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

public interface OutboxPayload {
	OutboxEventType eventType();
}
