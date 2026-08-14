package ru.itmo.nemat.weezzy.outbox.exception;

import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

public class OutboxEventHandlerNotFoundException extends RuntimeException {
	public OutboxEventHandlerNotFoundException(OutboxEventType eventType) {
		super("No outbox handler registered for event type: " + eventType);
	}
}
