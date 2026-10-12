package ru.itmo.nemat.weezzy.outbox.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.payload.TeamInvitationResolvedPayload;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationNotificationService;

@Component
@RequiredArgsConstructor
public class TeamInvitationResolvedEventHandler implements OutboxEventHandler {
	private final OutboxPayloadMapper payloadMapper;
	private final TeamInvitationNotificationService notificationService;

	@Override
	public OutboxEventType supportedType() {
		return OutboxEventType.TEAM_INVITATION_RESOLVED;
	}

	@Override
	public void handle(OutboxEvent event) {
		TeamInvitationResolvedPayload payload = payloadMapper.read(
				event, TeamInvitationResolvedPayload.class
		);
		notificationService.notifyResolved(payload.invitationId(), payload.status(), event.getId());
	}
}
