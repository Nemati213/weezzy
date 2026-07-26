package ru.itmo.nemat.weezzy.profile.dto;

import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProfileResponse(
		UUID id,
		String displayName,
		String bio,
		String telegram,
		String faculty,
		String studyProgram,
		Integer course,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		ProfileStatus status
) {
	public static ProfileResponse from(Profile profile) {
		return new ProfileResponse(
				profile.getId(),
				profile.getDisplayName(),
				profile.getBio(),
				profile.getTelegram(),
				profile.getFaculty(),
				profile.getStudyProgram(),
				profile.getCourse(),
				profile.getCreatedAt(),
				profile.getUpdatedAt(),
				profile.getStatus()
		);
	}
}
