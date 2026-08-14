package ru.itmo.nemat.weezzy.outbox.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchService;
import ru.itmo.nemat.weezzy.notification.NotificationService;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.payload.MatchCreatedPayload;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MatchCreatedEventHandler implements OutboxEventHandler {
	private final OutboxPayloadMapper payloadMapper;
	private final ProfileMatchService matchService;
	private final NotificationService notificationService;

	@Override
	public OutboxEventType supportedType() {
		return OutboxEventType.MATCH_CREATED;
	}

	@Override
	public void handle(OutboxEvent event) {
		MatchCreatedPayload payload = payloadMapper.read(
				event,
				MatchCreatedPayload.class
		);

		if (!matchService.hasMatch(
				payload.firstProfileId(),
				payload.secondProfileId()
		)) {
			return;
		}

		createNotification(
				payload.secondUserId(),
				payload.firstProfileId(),
				event.getId()
		);
		createNotification(
				payload.firstUserId(),
				payload.secondProfileId(),
				event.getId()
		);
	}

	private void createNotification(
			UUID recipientUserId,
			UUID matchedProfileId,
			UUID eventId
	) {
		notificationService.createIfAbsent(
				recipientUserId,
				NotificationType.NEW_MATCH,
				Map.of("matchedProfileId", matchedProfileId),
				eventId
		);
	}
}
