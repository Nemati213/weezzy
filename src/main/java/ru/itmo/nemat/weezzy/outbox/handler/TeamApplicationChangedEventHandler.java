package ru.itmo.nemat.weezzy.outbox.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.payload.TeamApplicationChangedPayload;
import ru.itmo.nemat.weezzy.team.application.TeamApplicationNotificationService;

@Component
@RequiredArgsConstructor
public class TeamApplicationChangedEventHandler implements OutboxEventHandler {
	private final OutboxPayloadMapper payloadMapper;
	private final TeamApplicationNotificationService notificationService;

	@Override
	public OutboxEventType supportedType() {
		return OutboxEventType.TEAM_APPLICATION_CHANGED;
	}

	@Override
	public void handle(OutboxEvent event) {
		TeamApplicationChangedPayload payload = payloadMapper.read(
				event, TeamApplicationChangedPayload.class);
		notificationService.notifyChanged(payload.applicationId(), payload.status(), event.getId());
	}
}
