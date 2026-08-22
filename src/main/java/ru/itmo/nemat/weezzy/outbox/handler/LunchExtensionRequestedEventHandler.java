package ru.itmo.nemat.weezzy.outbox.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.lunch.config.LunchProperties;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestRepository;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestStatus;
import ru.itmo.nemat.weezzy.notification.NotificationService;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.payload.LunchExtensionRequestedPayload;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class LunchExtensionRequestedEventHandler implements OutboxEventHandler {
	private final OutboxPayloadMapper payloadMapper;
	private final LunchRequestRepository requestRepository;
	private final NotificationService notificationService;
	private final LunchProperties properties;
	private final Clock clock;

	@Override
	public OutboxEventType supportedType() {
		return OutboxEventType.LUNCH_EXTENSION_REQUESTED;
	}

	@Override
	public void handle(OutboxEvent event) {
		LunchExtensionRequestedPayload payload = payloadMapper.read(
				event,
				LunchExtensionRequestedPayload.class
		);
		LunchRequest request = requestRepository.findForExtensionNotification(
				payload.requestId()
		).orElse(null);
		if (!isCurrentOffer(request, payload)) {
			return;
		}

		notificationService.createIfAbsent(
				request.getProfile().getUser().getId(),
				NotificationType.LUNCH_EXTENSION_REQUESTED,
				Map.of(
						"requestId", request.getId(),
						"offerId", request.getExtensionOfferId(),
						"timeSlot", request.getTimeSlot(),
						"targetTimeSlot", request.getExtensionTargetTimeSlot(),
						"expiresAt", request.getExtensionExpiresAt(),
						"topic", request.getTopic()
				),
				event.getId()
		);
	}

	private boolean isCurrentOffer(
			LunchRequest request,
			LunchExtensionRequestedPayload payload
	) {
		LocalDateTime now = now();
		return request != null
				&& request.getStatus() == LunchRequestStatus.EXTENSION_REQUESTED
				&& Objects.equals(request.getExtensionOfferId(), payload.offerId())
				&& request.getExtensionExpiresAt() != null
				&& request.getExtensionTargetTimeSlot() != null
				&& request.getProfile().getUser() != null
				&& now.isBefore(request.getExtensionExpiresAt())
				&& now.isBefore(request.getExtensionTargetTimeSlot());
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), properties.zoneId());
	}
}
