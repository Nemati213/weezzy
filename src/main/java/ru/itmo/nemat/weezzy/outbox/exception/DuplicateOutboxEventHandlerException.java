package ru.itmo.nemat.weezzy.outbox.exception;

import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

public class DuplicateOutboxEventHandlerException extends RuntimeException {
	public DuplicateOutboxEventHandlerException(OutboxEventType eventType) {
		super("Multiple outbox handlers registered for event type: " + eventType);
	}
}
