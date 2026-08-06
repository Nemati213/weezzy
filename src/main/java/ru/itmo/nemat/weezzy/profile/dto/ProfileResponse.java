package ru.itmo.nemat.weezzy.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProfileResponse(
		UUID id,
		String displayName,
		String bio,
		@JsonInclude(JsonInclude.Include.NON_NULL)
		String telegram,
		String faculty,
		String studyProgram,
		Integer course,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		ProfileStatus status,
		UUID userId,
		boolean deleted
) {
	public static ProfileResponse from(Profile profile) {
		return toResponse(profile, false);
	}

	public static ProfileResponse withContact(Profile profile) {
		return toResponse(profile, true);
	}

	private static ProfileResponse toResponse(Profile profile, boolean includeContact) {
		boolean deleted = profile.getStatus() == ProfileStatus.DELETED;
		return new ProfileResponse(
				profile.getId(),
				profile.getDisplayName(),
				profile.getBio(),
				includeContact && !deleted ? profile.getTelegram() : null,
				profile.getFaculty(),
				profile.getStudyProgram(),
				profile.getCourse(),
				profile.getCreatedAt(),
				profile.getUpdatedAt(),
				profile.getStatus(),
				profile.getUser() == null ? null : profile.getUser().getId(),
				deleted
		);
	}
}
