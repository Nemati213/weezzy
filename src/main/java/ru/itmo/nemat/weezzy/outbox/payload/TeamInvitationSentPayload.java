package ru.itmo.nemat.weezzy.outbox.payload;

import jakarta.validation.constraints.NotNull;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;

import java.util.UUID;

public record TeamInvitationSentPayload(@NotNull UUID invitationId) implements OutboxPayload {
	@Override
	public OutboxEventType eventType() {
		return OutboxEventType.TEAM_INVITATION_SENT;
	}
}
