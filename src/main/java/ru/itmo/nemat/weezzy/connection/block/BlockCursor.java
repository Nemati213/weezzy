package ru.itmo.nemat.weezzy.connection.block;

import java.time.LocalDateTime;
import java.util.UUID;

record BlockCursor(LocalDateTime createdAt, UUID blockedProfileId) {
}
