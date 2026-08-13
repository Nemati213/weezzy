package ru.itmo.nemat.weezzy.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.common.pagination.CursorTokenCodec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class NotificationCursorCodec {
    private static final String CURSOR_TYPE = "notification";

    private final CursorTokenCodec tokenCodec;

    String encode(NotificationCursor cursor) {
        return tokenCodec.encode(CURSOR_TYPE, List.of(
                cursor.createdAt().toString(),
                cursor.notificationId().toString()
        ));
    }

    NotificationCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }

        try {
            List<String> values = tokenCodec.decode(encoded, CURSOR_TYPE, 2);
            return new NotificationCursor(
                    LocalDateTime.parse(values.get(0)),
                    UUID.fromString(values.get(1))
            );
        } catch (RuntimeException exception) {
            throw new InvalidNotificationCursorException();
        }
    }
}
