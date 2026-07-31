package ru.itmo.nemat.weezzy.connection.match;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.ProfilePairLockService;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.connection.match.dto.ProfileMatchResponse;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileMatchService {
	private final ProfileMatchRepository repository;
	private final ProfileService profileService;
	private final ProfileBlockService blockService;
	private final ProfilePairLockService pairLockService;

	@Transactional
	public ProfileMatch create(UUID firstProfileId, UUID secondProfileId) {
		if (firstProfileId.equals(secondProfileId)) {
			throw new SelfMatchException(firstProfileId);
		}

		pairLockService.lock(firstProfileId, secondProfileId);
		blockService.ensureInteractionAllowed(firstProfileId, secondProfileId);

		ProfileMatchId matchId = normalizedId(firstProfileId, secondProfileId);

		return repository.findById(matchId)
				.orElseGet(() -> saveNewMatch(matchId));
	}

	@Transactional(readOnly = true)
	public List<ProfileMatchResponse> findAllMatchesByProfileId(UUID profileId) {
		profileService.findById(profileId);

		List<ProfileMatch> matches =
				repository.findByFirstProfileIdOrSecondProfileIdOrderByCreatedAtDesc(
						profileId,
						profileId
				);

		Set<UUID> matchedProfileIds = matches.stream()
				.map(profileMatch -> otherProfileId(profileId, profileMatch))
				.collect(Collectors.toSet());

		Map<UUID, Profile> profilesById = profileService.findAllByIds(matchedProfileIds)
				.stream()
				.collect(Collectors.toMap(
						Profile::getId,
						Function.identity()
				));

		return matches
				.stream()
				.map(profileMatch -> ProfileMatchResponse.from(
						profileMatch,
						profilesById.get(otherProfileId(profileId, profileMatch))
				))
				.toList();
	}

	private UUID otherProfileId(UUID sourceProfileId, ProfileMatch profileMatch) {
		if (sourceProfileId.equals(profileMatch.getFirstProfileId())) {
			return profileMatch.getSecondProfileId();
		}
		if (sourceProfileId.equals(profileMatch.getSecondProfileId())) {
			return profileMatch.getFirstProfileId();
		}
		throw new IllegalArgumentException("Profile is not part of match: " + sourceProfileId);
	}

	private ProfileMatch saveNewMatch(ProfileMatchId matchId) {
		ProfileMatch profileMatch = new ProfileMatch();
		profileMatch.setFirstProfileId(matchId.getFirstProfileId());
		profileMatch.setSecondProfileId(matchId.getSecondProfileId());

		return repository.saveAndFlush(profileMatch);
	}

	private ProfileMatchId normalizedId(UUID firstProfileId, UUID secondProfileId) {
		if (firstProfileId.toString().compareTo(secondProfileId.toString()) < 0) {
			return new ProfileMatchId(firstProfileId, secondProfileId);
		}

		return new ProfileMatchId(secondProfileId, firstProfileId);
	}
}
