package ru.itmo.nemat.weezzy.profile.photo.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PhotoUploadResponse(
        UUID photoId,
        String uploadUrl,
        LocalDateTime expiresAt
) {
}
