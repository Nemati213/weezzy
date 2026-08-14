package ru.itmo.nemat.weezzy.outbox.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionService;
import ru.itmo.nemat.weezzy.notification.NotificationService;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.payload.AccountSanctionCreatedPayload;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AccountSanctionCreatedEventHandler implements OutboxEventHandler {
	private final OutboxPayloadMapper payloadMapper;
	private final AccountSanctionService sanctionService;
	private final NotificationService notificationService;

	@Override
	public OutboxEventType supportedType() {
		return OutboxEventType.ACCOUNT_SANCTION_CREATED;
	}

	@Override
	public void handle(OutboxEvent event) {
		AccountSanctionCreatedPayload payload = payloadMapper.read(
				event,
				AccountSanctionCreatedPayload.class
		);
		if (!sanctionService.isEffective(
				payload.sanctionId(),
				payload.recipientUserId()
		)) {
			return;
		}

		notificationService.createIfAbsent(
				payload.recipientUserId(),
				NotificationType.ADMIN_SANCTION,
				notificationPayload(payload),
				event.getId()
		);
	}

	private Map<String, Object> notificationPayload(
			AccountSanctionCreatedPayload payload
	) {
		Map<String, Object> notificationPayload = new LinkedHashMap<>();
		notificationPayload.put("sanctionId", payload.sanctionId());
		notificationPayload.put("type", payload.type());
		notificationPayload.put("reason", payload.reason());
		if (payload.expiresAt() != null) {
			notificationPayload.put("expiresAt", payload.expiresAt());
		}
		if (payload.sourceReportId() != null) {
			notificationPayload.put("sourceReportId", payload.sourceReportId());
		}
		return notificationPayload;
	}
}
