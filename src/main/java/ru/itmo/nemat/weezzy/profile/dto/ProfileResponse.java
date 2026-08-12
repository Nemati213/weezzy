package ru.itmo.nemat.weezzy.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.profile.photo.dto.ProfilePhotoResponse;

import java.time.LocalDateTime;
import java.util.List;
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
		boolean deleted,
		List<ProfilePhotoResponse> photos
) {
	public static ProfileResponse from(
			Profile profile,
			List<ProfilePhotoResponse> photos
	) {
		return toResponse(profile, false, photos);
	}

	public static ProfileResponse withContact(
			Profile profile,
			List<ProfilePhotoResponse> photos
	) {
		return toResponse(profile, true, photos);
	}

	public static ProfileResponse unavailable(UUID profileId) {
		return new ProfileResponse(
				profileId,
				"Unavailable profile",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				ProfileStatus.HIDDEN,
				null,
				false,
				List.of()
		);
	}

	private static ProfileResponse toResponse(
			Profile profile,
			boolean includeContact,
			List<ProfilePhotoResponse> photos
	) {
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
				deleted,
				deleted ? List.of() : List.copyOf(photos)
		);
	}
}
