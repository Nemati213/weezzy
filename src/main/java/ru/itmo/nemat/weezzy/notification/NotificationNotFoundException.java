package ru.itmo.nemat.weezzy.notification;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class NotificationNotFoundException extends NotFoundException {
    public NotificationNotFoundException(UUID notificationId) {
        super("Notification with id " + notificationId + " not found");
    }
}
