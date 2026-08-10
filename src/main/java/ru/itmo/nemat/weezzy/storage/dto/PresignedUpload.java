package ru.itmo.nemat.weezzy.storage.dto;

import java.time.LocalDateTime;

public record PresignedUpload(
        String uploadUrl,
        LocalDateTime expiresAt
) {
}
