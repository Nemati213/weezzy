package ru.itmo.nemat.weezzy.notification;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidNotificationCursorException extends BadRequestException {
    public InvalidNotificationCursorException() {
        super("Invalid notification cursor");
    }
}
