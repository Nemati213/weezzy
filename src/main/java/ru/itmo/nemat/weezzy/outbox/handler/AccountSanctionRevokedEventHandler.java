package ru.itmo.nemat.weezzy.outbox.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionService;
import ru.itmo.nemat.weezzy.notification.NotificationService;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.payload.AccountSanctionRevokedPayload;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AccountSanctionRevokedEventHandler implements OutboxEventHandler {
	private final OutboxPayloadMapper payloadMapper;
	private final AccountSanctionService sanctionService;
	private final NotificationService notificationService;

	@Override
	public OutboxEventType supportedType() {
		return OutboxEventType.ACCOUNT_SANCTION_REVOKED;
	}

	@Override
	public void handle(OutboxEvent event) {
		AccountSanctionRevokedPayload payload = payloadMapper.read(
				event,
				AccountSanctionRevokedPayload.class
		);
		if (!sanctionService.hasMatchingRevocation(
				payload.sanctionId(),
				payload.recipientUserId(),
				payload.type(),
				payload.revocationReason()
		)) {
			return;
		}

		notificationService.createIfAbsent(
				payload.recipientUserId(),
				NotificationType.ADMIN_SANCTION_REVOKED,
				Map.of(
						"sanctionId", payload.sanctionId(),
						"type", payload.type(),
						"revocationReason", payload.revocationReason(),
						"revokedAt", payload.revokedAt()
				),
				event.getId()
		);
	}
}
