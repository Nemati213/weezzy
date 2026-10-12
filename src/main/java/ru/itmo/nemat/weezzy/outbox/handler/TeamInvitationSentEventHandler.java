package ru.itmo.nemat.weezzy.outbox.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.payload.TeamInvitationSentPayload;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationNotificationService;

@Component
@RequiredArgsConstructor
public class TeamInvitationSentEventHandler implements OutboxEventHandler {
	private final OutboxPayloadMapper payloadMapper;
	private final TeamInvitationNotificationService notificationService;

	@Override
	public OutboxEventType supportedType() {
		return OutboxEventType.TEAM_INVITATION_SENT;
	}

	@Override
	public void handle(OutboxEvent event) {
		TeamInvitationSentPayload payload = payloadMapper.read(
				event, TeamInvitationSentPayload.class
		);
		notificationService.notifySent(payload.invitationId(), event.getId());
	}
}
