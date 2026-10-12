package ru.itmo.nemat.weezzy.outbox.payload;

import jakarta.validation.constraints.NotNull;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationStatus;

import java.util.UUID;

public record TeamInvitationResolvedPayload(
		@NotNull UUID invitationId,
		@NotNull TeamInvitationStatus status
) implements OutboxPayload {
	public TeamInvitationResolvedPayload {
		if (status == TeamInvitationStatus.PENDING) {
			throw new IllegalArgumentException("Invitation is not resolved");
		}
	}

	@Override
	public OutboxEventType eventType() {
		return OutboxEventType.TEAM_INVITATION_RESOLVED;
	}
}
