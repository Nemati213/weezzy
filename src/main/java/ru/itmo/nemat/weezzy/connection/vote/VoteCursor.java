package ru.itmo.nemat.weezzy.connection.vote;

import java.time.LocalDateTime;
import java.util.UUID;

public record VoteCursor(
		LocalDateTime createdAt,
		UUID targetProfileId
) {
}
