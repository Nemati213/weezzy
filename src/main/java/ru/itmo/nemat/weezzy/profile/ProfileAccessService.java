package ru.itmo.nemat.weezzy.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchService;
import ru.itmo.nemat.weezzy.profile.dto.ProfileResponse;
import ru.itmo.nemat.weezzy.profile.photo.ProfilePhotoService;
import ru.itmo.nemat.weezzy.profile.photo.dto.ProfilePhotoResponse;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileAccessService {
	private final ProfileService profileService;
	private final ProfileMatchService matchService;
	private final ProfileBlockService blockService;
	private final ProfilePhotoService photoService;

	@Transactional(readOnly = true)
	public ProfileResponse findByIdForUser(UUID viewerUserId, UUID targetProfileId) {
		Profile targetProfile = profileService.findById(targetProfileId);
		boolean includeContact = profileService.findOptionalByUserId(viewerUserId)
				.map(viewerProfile -> canViewContact(
						viewerProfile,
						targetProfile
				))
				.orElse(false);
		List<ProfilePhotoResponse> photos =
				photoService.findReadyPhotos(targetProfileId);
		return includeContact
				? ProfileResponse.withContact(targetProfile, photos)
				: ProfileResponse.from(targetProfile, photos);
	}

	private boolean canViewContact(
			Profile viewerProfile,
			Profile targetProfile
	) {
		if (viewerProfile.getId().equals(targetProfile.getId())) {
			return true;
		}
		if (blockService.isBlockedBetween(viewerProfile.getId(), targetProfile.getId())) {
			throw new ProfileNotFoundException(targetProfile.getId());
		}

		return matchService.hasMatch(viewerProfile.getId(), targetProfile.getId());
	}
}
