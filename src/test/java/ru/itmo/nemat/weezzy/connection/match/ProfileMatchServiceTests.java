package ru.itmo.nemat.weezzy.connection.match;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itmo.nemat.weezzy.connection.ProfilePairLockService;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.connection.block.ProfileInteractionBlockedException;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.photo.ProfilePhotoService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileMatchServiceTests {

	@Mock
	private ProfileMatchRepository matchRepository;

	@Mock
	private ProfileService profileService;

	@Mock
	private ProfileBlockService blockService;

	@Mock
	private ProfilePairLockService pairLockService;

	@Mock
	private ProfilePhotoService profilePhotoService;

	@InjectMocks
	private ProfileMatchService matchService;

	@Test
	void findAllMatchesLoadsMatchedProfilesInSingleBulkCall() {
		UUID sourceProfileId = UUID.randomUUID();
		Profile firstMatchedProfile = profile(UUID.randomUUID(), "First matched profile");
		Profile secondMatchedProfile = profile(UUID.randomUUID(), "Second matched profile");
		List<ProfileMatch> matches = List.of(
				match(sourceProfileId, firstMatchedProfile.getId()),
				match(secondMatchedProfile.getId(), sourceProfileId)
		);
		Set<UUID> matchedProfileIds = Set.of(
				firstMatchedProfile.getId(),
				secondMatchedProfile.getId()
		);
		when(matchRepository
				.findByFirstProfileIdOrSecondProfileIdOrderByCreatedAtDesc(
						sourceProfileId,
						sourceProfileId
				))
				.thenReturn(matches);
		when(profileService.findAllByIds(matchedProfileIds))
				.thenReturn(List.of(firstMatchedProfile, secondMatchedProfile));
		when(profilePhotoService.findReadyPhotosByProfileIds(matchedProfileIds))
				.thenReturn(Map.of());

		var responses = matchService.findAllMatchesByProfileId(sourceProfileId);

		assertThat(responses)
				.extracting(response -> response.matchedProfile().id())
				.containsExactly(
						firstMatchedProfile.getId(),
						secondMatchedProfile.getId()
				);
		verify(profileService, times(1)).findById(sourceProfileId);
		verify(profileService, times(1)).findAllByIds(matchedProfileIds);
		verify(profilePhotoService, times(1))
				.findReadyPhotosByProfileIds(matchedProfileIds);
	}

	@Test
	void createRejectsProfilesBlockedInEitherDirection() {
		UUID firstProfileId = UUID.randomUUID();
		UUID secondProfileId = UUID.randomUUID();
		doThrow(new ProfileInteractionBlockedException(
				firstProfileId,
				secondProfileId
		)).when(blockService).ensureInteractionAllowed(
				firstProfileId,
				secondProfileId
		);

		assertThatThrownBy(() -> matchService.create(firstProfileId, secondProfileId))
				.isInstanceOf(ProfileInteractionBlockedException.class);

		verify(pairLockService).lock(firstProfileId, secondProfileId);
		verifyNoInteractions(matchRepository);
	}

	private Profile profile(UUID id, String displayName) {
		Profile profile = new Profile();
		profile.setId(id);
		profile.setDisplayName(displayName);
		return profile;
	}

	private ProfileMatch match(UUID firstProfileId, UUID secondProfileId) {
		ProfileMatch match = new ProfileMatch();
		match.setFirstProfileId(firstProfileId);
		match.setSecondProfileId(secondProfileId);
		return match;
	}
}
