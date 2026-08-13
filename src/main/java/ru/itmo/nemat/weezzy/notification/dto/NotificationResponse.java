package ru.itmo.nemat.weezzy.notification.dto;

import ru.itmo.nemat.weezzy.notification.Notification;
import ru.itmo.nemat.weezzy.notification.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        Map<String, Object> payload,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getPayload(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
