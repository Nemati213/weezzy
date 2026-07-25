package ru.itmo.nemat.weezzy.profile.interest;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.interest.Interest;
import ru.itmo.nemat.weezzy.interest.InterestService;
import ru.itmo.nemat.weezzy.profile.ProfileService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileInterestService {
	private final ProfileService profileService;
	private final InterestService interestService;
	private final ProfileInterestRepository profileInterestRepository;

	@Transactional
	public Interest addInterest(UUID profileId, UUID interestId) {
		profileService.findById(profileId);
		Interest interest = interestService.findById(interestId);

		if (profileInterestRepository.existsByProfileIdAndInterestId(profileId, interestId)) {
			throw new ProfileInterestAlreadyExistsException(profileId, interestId);
		}

		ProfileInterest profileInterest = new ProfileInterest();
		profileInterest.setProfileId(profileId);
		profileInterest.setInterestId(interestId);
		profileInterestRepository.save(profileInterest);

		return interest;
	}

	@Transactional(readOnly = true)
	public List<Interest> findInterests(UUID profileId) {
		profileService.findById(profileId);

		return profileInterestRepository.findAllByProfileId(profileId).stream()
				.map(ProfileInterest::getInterestId)
				.map(interestService::findById)
				.toList();
	}

	@Transactional
	public void removeInterest(UUID profileId, UUID interestId) {
		profileService.findById(profileId);
		interestService.findById(interestId);

		if (!profileInterestRepository.existsByProfileIdAndInterestId(profileId, interestId)) {
			throw new ProfileInterestNotFoundException(profileId, interestId);
		}

		profileInterestRepository.deleteByProfileIdAndInterestId(profileId, interestId);
	}
}
