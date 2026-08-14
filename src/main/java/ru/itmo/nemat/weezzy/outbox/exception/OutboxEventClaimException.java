package ru.itmo.nemat.weezzy.outbox.exception;

import ru.itmo.nemat.weezzy.outbox.OutboxEventStatus;

import java.util.UUID;

public class OutboxEventClaimException extends RuntimeException {
	public OutboxEventClaimException(
			UUID eventId,
			OutboxEventStatus status,
			String actualWorkerId,
			String expectedWorkerId
	) {
		super("Outbox event %s is not claimed by worker %s (status=%s, lockedBy=%s)"
				.formatted(eventId, expectedWorkerId, status, actualWorkerId));
	}
}
