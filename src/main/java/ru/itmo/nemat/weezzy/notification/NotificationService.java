package ru.itmo.nemat.weezzy.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.common.dto.CursorPageResponse;
import ru.itmo.nemat.weezzy.notification.dto.NotificationResponse;
import ru.itmo.nemat.weezzy.user.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {
	private final NotificationRepository repository;
	private final UserService userService;
	private final NotificationCursorCodec cursorCodec;

	@Transactional
	public Notification createIfAbsent(
			UUID recipientUserId,
			NotificationType type,
			Map<String, Object> payload,
			UUID sourceEventId
	) {
		userService.findById(recipientUserId);

		return repository.findByRecipientUserIdAndSourceEventId(
				recipientUserId,
				sourceEventId
		).orElseGet(() -> create(
				recipientUserId,
				type,
				payload,
				sourceEventId
		));
	}

	@Transactional(readOnly = true)
	public CursorPageResponse<NotificationResponse> findPage(
			UUID userId,
			String encodedCursor,
			int limit
	) {
		userService.findById(userId);
		NotificationCursor cursor = cursorCodec.decode(encodedCursor);
		PageRequest pageRequest = PageRequest.of(0, limit + 1);

		List<Notification> fetched = cursor == null
				? repository.findFirstPage(userId, pageRequest)
				: repository.findNextPage(
						userId,
						cursor.createdAt(),
						cursor.notificationId(),
						pageRequest
				);

		boolean hasNext = fetched.size() > limit;
		List<Notification> page = fetched.stream().limit(limit).toList();
		String nextCursor = hasNext
				? cursorCodec.encode(toCursor(page.getLast()))
				: null;

		return new CursorPageResponse<>(
				page.stream()
						.map(NotificationResponse::from)
						.toList(),
				nextCursor
		);
	}

	@Transactional
	public Notification markAsRead(UUID userId, UUID notificationId) {
		userService.findById(userId);
		Notification notification = repository.findByIdAndRecipientUserId(
				notificationId,
				userId
		).orElseThrow(() -> new NotificationNotFoundException(notificationId));

		if (notification.getReadAt() == null) {
			notification.setReadAt(LocalDateTime.now());
		}

		return notification;
	}

	@Transactional
	public int markAllAsRead(UUID userId) {
		userService.findById(userId);
		return repository.markAllAsRead(userId, LocalDateTime.now());
	}

	private Notification create(
			UUID recipientUserId,
			NotificationType type,
			Map<String, Object> payload,
			UUID sourceEventId
	) {
		Notification notification = new Notification();
		notification.setRecipientUserId(recipientUserId);
		notification.setType(type);
		notification.setPayload(payload);
		notification.setSourceEventId(sourceEventId);
		return repository.save(notification);
	}

	private NotificationCursor toCursor(Notification notification) {
		return new NotificationCursor(
				notification.getCreatedAt(),
				notification.getId()
		);
	}
}
