package ru.itmo.nemat.weezzy.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

public final class ProfilePhotoTestData {
	private ProfilePhotoTestData() {
	}

	public static UUID insertReadyPhoto(JdbcTemplate jdbcTemplate, UUID profileId) {
		UUID photoId = UUID.randomUUID();
		jdbcTemplate.update(
				"""
				INSERT INTO profile_photos (
				    id, profile_id, object_key, content_type, size_bytes,
				    position, is_avatar, status, uploaded_at
				)
				VALUES (?, ?, ?, 'image/jpeg', 100, 0, TRUE, 'READY', NOW())
				""",
				photoId,
				profileId,
				"test/profiles/" + profileId + "/" + photoId
		);
		return photoId;
	}
}
