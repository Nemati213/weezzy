package ru.itmo.nemat.weezzy.notification;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationCursor(LocalDateTime createdAt, UUID notificationId) {
}
