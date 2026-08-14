package ru.itmo.nemat.weezzy.outbox.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.moderation.report.ProfileReportService;
import ru.itmo.nemat.weezzy.notification.NotificationService;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.payload.ProfileReportDecidedPayload;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProfileReportDecidedEventHandler implements OutboxEventHandler {
	private final OutboxPayloadMapper payloadMapper;
	private final ProfileReportService reportService;
	private final NotificationService notificationService;

	@Override
	public OutboxEventType supportedType() {
		return OutboxEventType.REPORT_DECIDED;
	}

	@Override
	public void handle(OutboxEvent event) {
		ProfileReportDecidedPayload payload = payloadMapper.read(
				event,
				ProfileReportDecidedPayload.class
		);
		if (!reportService.hasMatchingDecision(
				payload.reportId(),
				payload.recipientUserId(),
				payload.targetProfileId(),
				payload.status(),
				payload.decision()
		)) {
			return;
		}

		notificationService.createIfAbsent(
				payload.recipientUserId(),
				NotificationType.REPORT_DECISION,
				Map.of(
						"reportId", payload.reportId(),
						"targetProfileId", payload.targetProfileId(),
						"status", payload.status(),
						"decision", payload.decision()
				),
				event.getId()
		);
	}
}
