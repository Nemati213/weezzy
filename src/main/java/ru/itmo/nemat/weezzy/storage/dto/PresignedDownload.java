package ru.itmo.nemat.weezzy.storage.dto;

import java.time.LocalDateTime;

public record PresignedDownload(
		String downloadUrl,
		LocalDateTime expiresAt
) {
}
