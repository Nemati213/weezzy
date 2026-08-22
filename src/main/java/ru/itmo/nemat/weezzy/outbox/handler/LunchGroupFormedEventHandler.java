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
import ru.itmo.nemat.weezzy.outbox.payload.LunchGroupFormedPayload;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class LunchGroupFormedEventHandler implements OutboxEventHandler {
	private final OutboxPayloadMapper payloadMapper;
	private final LunchGroupRepository groupRepository;
	private final LunchGroupMemberRepository memberRepository;
	private final NotificationService notificationService;

	@Override
	public OutboxEventType supportedType() {
		return OutboxEventType.LUNCH_GROUP_FORMED;
	}

	@Override
	public void handle(OutboxEvent event) {
		LunchGroupFormedPayload payload = payloadMapper.read(
				event,
				LunchGroupFormedPayload.class
		);
		LunchGroup group = groupRepository.findByIdAndStatus(
				payload.groupId(),
				LunchGroupStatus.ACTIVE
		).orElse(null);
		if (group == null) {
			return;
		}

		Map<String, Object> notificationPayload = Map.of(
				"groupId", group.getId(),
				"locationId", group.getLocation().getId(),
				"timeSlot", group.getTimeSlot(),
				"topic", group.getTopic()
		);
		memberRepository.findByGroupIdOrderByJoinedAtAsc(group.getId()).stream()
				.map(member -> member.getProfile().getUser())
				.filter(user -> user != null)
				.forEach(user -> notificationService.createIfAbsent(
						user.getId(),
						NotificationType.LUNCH_GROUP_FORMED,
						notificationPayload,
						event.getId()
				));
	}
}
