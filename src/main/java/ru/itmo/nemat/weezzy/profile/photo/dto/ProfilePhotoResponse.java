package ru.itmo.nemat.weezzy.profile.photo.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProfilePhotoResponse(
		UUID id,
		String downloadUrl,
		LocalDateTime downloadUrlExpiresAt,
		String contentType,
		long sizeBytes,
		int position,
		boolean avatar
) {
}
