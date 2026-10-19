package ru.itmo.nemat.weezzy.outbox.payload;

import jakarta.validation.constraints.NotNull;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.team.application.TeamApplicationStatus;

import java.util.UUID;

public record TeamApplicationChangedPayload(
		@NotNull UUID applicationId, @NotNull TeamApplicationStatus status
) implements OutboxPayload {
	@Override
	public OutboxEventType eventType() {
		return OutboxEventType.TEAM_APPLICATION_CHANGED;
	}
}
