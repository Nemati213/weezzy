package ru.itmo.nemat.weezzy.outbox.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.connection.vote.ProfileVoteService;
import ru.itmo.nemat.weezzy.notification.NotificationService;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.payload.ProfileLikedPayload;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProfileLikedEventHandler implements OutboxEventHandler {
	private final OutboxPayloadMapper payloadMapper;
	private final ProfileVoteService profileVoteService;
	private final NotificationService notificationService;

	@Override
	public OutboxEventType supportedType() {
		return OutboxEventType.PROFILE_LIKED;
	}

	@Override
	public void handle(OutboxEvent event) {
		ProfileLikedPayload payload = payloadMapper.read(
				event,
				ProfileLikedPayload.class
		);

		if (!profileVoteService.existsLike(
				payload.sourceProfileId(),
				payload.targetProfileId()
		)) {
			return;
		}

		notificationService.createIfAbsent(
				payload.recipientUserId(),
				NotificationType.NEW_LIKE,
				Map.of("sourceProfileId", payload.sourceProfileId()),
				event.getId()
		);
	}
}
