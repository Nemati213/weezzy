package ru.itmo.nemat.weezzy.outbox;

public enum OutboxEventStatus {
	PENDING,
	PROCESSING,
	PROCESSED,
	FAILED
}
