package ru.itmo.nemat.weezzy.connection.match;

import java.time.LocalDateTime;
import java.util.UUID;

record MatchCursor(
		LocalDateTime createdAt,
		UUID firstProfileId,
		UUID secondProfileId
) {
}
