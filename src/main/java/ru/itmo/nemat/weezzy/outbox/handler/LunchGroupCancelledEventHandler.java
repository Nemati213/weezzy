package ru.itmo.nemat.weezzy.outbox.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroup;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMemberRepository;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupRepository;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupStatus;
import ru.itmo.nemat.weezzy.notification.NotificationService;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.outbox.OutboxEvent;
import ru.itmo.nemat.weezzy.outbox.OutboxEventType;
import ru.itmo.nemat.weezzy.outbox.payload.LunchGroupCancelledPayload;

import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class LunchGroupCancelledEventHandler implements OutboxEventHandler {
	private final OutboxPayloadMapper payloadMapper;
	private final LunchGroupRepository groupRepository;
	private final LunchGroupMemberRepository memberRepository;
	private final NotificationService notificationService;

	@Override
	public OutboxEventType supportedType() {
		return OutboxEventType.LUNCH_GROUP_CANCELLED;
	}

	@Override
	public void handle(OutboxEvent event) {
		LunchGroupCancelledPayload payload = payloadMapper.read(
				event,
				LunchGroupCancelledPayload.class
		);
		LunchGroup group = groupRepository.findByIdAndStatus(
				payload.groupId(),
				LunchGroupStatus.CANCELLED
		).orElse(null);
		if (group == null
				|| !Objects.equals(group.getCancellationReason(), payload.reason())) {
			return;
		}

		Map<String, Object> notificationPayload = Map.of(
				"groupId", group.getId(),
				"locationId", group.getLocation().getId(),
				"timeSlot", group.getTimeSlot(),
				"topic", group.getTopic(),
				"reason", group.getCancellationReason()
		);
		memberRepository.findByGroupIdOrderByJoinedAtAsc(group.getId()).stream()
				.map(member -> member.getProfile().getUser())
				.filter(Objects::nonNull)
				.forEach(user -> notificationService.createIfAbsent(
						user.getId(),
						NotificationType.LUNCH_GROUP_CANCELLED,
						notificationPayload,
						event.getId()
				));
	}
}
