package ru.itmo.nemat.weezzy.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchService;
import ru.itmo.nemat.weezzy.profile.dto.ProfileResponse;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileAccessService {
	private final ProfileService profileService;
	private final ProfileMatchService matchService;
	private final ProfileBlockService blockService;

	@Transactional(readOnly = true)
	public ProfileResponse findByIdForUser(UUID viewerUserId, UUID targetProfileId) {
		Profile targetProfile = profileService.findById(targetProfileId);
		return profileService.findOptionalByUserId(viewerUserId)
				.map(viewerProfile -> responseForViewer(viewerProfile, targetProfile))
				.orElseGet(() -> ProfileResponse.from(targetProfile));
	}

	private ProfileResponse responseForViewer(Profile viewerProfile, Profile targetProfile) {
		if (viewerProfile.getId().equals(targetProfile.getId())) {
			return ProfileResponse.withContact(targetProfile);
		}
		if (blockService.isBlockedBetween(viewerProfile.getId(), targetProfile.getId())) {
			throw new ProfileNotFoundException(targetProfile.getId());
		}

		return matchService.hasMatch(viewerProfile.getId(), targetProfile.getId())
				? ProfileResponse.withContact(targetProfile)
				: ProfileResponse.from(targetProfile);
	}
}
