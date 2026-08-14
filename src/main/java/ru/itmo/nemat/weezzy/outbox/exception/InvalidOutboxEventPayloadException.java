package ru.itmo.nemat.weezzy.outbox.exception;

import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

import java.util.UUID;

public class InvalidOutboxEventPayloadException extends RuntimeException {
	public InvalidOutboxEventPayloadException(
			UUID eventId,
			OutboxEventType eventType,
			Throwable cause
	) {
		super("Invalid payload for outbox event %s of type %s"
				.formatted(eventId, eventType), cause);
	}
}
