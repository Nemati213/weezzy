package ru.itmo.nemat.weezzy.connection.match;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.match.dto.ProfileMatchResponse;
import ru.itmo.nemat.weezzy.profile.ProfileService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileMatchService {
	private final ProfileMatchRepository repository;
	private final ProfileService profileService;

	@Transactional
	public ProfileMatch create(UUID firstProfileId, UUID secondProfileId) {
		if (firstProfileId.equals(secondProfileId)) {
			throw new SelfMatchException(firstProfileId);
		}

		profileService.findById(firstProfileId);
		profileService.findById(secondProfileId);

		ProfileMatchId matchId = normalizedId(firstProfileId, secondProfileId);

		return repository.findById(matchId)
				.orElseGet(() -> saveNewMatch(matchId));
	}

	@Transactional(readOnly = true)
	public List<ProfileMatchResponse> findAllMatchesByProfileId(UUID profileId) {
		profileService.findById(profileId);

		return repository.findByFirstProfileIdOrSecondProfileId(profileId, profileId)
				.stream()
				.map(profileMatch -> ProfileMatchResponse.from(
						profileMatch,
						profileService.findById(otherProfileId(profileId, profileMatch))
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

		try {
			return repository.saveAndFlush(profileMatch);
		} catch (DataIntegrityViolationException exception) {
			return repository.findById(matchId).orElseThrow(() -> exception);
		}
	}

	private ProfileMatchId normalizedId(UUID firstProfileId, UUID secondProfileId) {
		if (firstProfileId.toString().compareTo(secondProfileId.toString()) < 0) {
			return new ProfileMatchId(firstProfileId, secondProfileId);
		}

		return new ProfileMatchId(secondProfileId, firstProfileId);
	}
}
