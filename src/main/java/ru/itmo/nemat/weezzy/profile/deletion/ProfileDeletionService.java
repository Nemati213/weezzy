package ru.itmo.nemat.weezzy.profile.deletion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockRepository;
import ru.itmo.nemat.weezzy.connection.event.ProfileInteractionEventRepository;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoalRepository;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterestRepository;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkillRepository;
import ru.itmo.nemat.weezzy.recommendation.impression.ProfileRecommendationImpressionRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileDeletionService {
	private static final String DELETED_DISPLAY_NAME = "Deleted account";

	private final ProfileRepository profileRepository;
	private final ProfileSkillRepository profileSkillRepository;
	private final ProfileInterestRepository profileInterestRepository;
	private final ProfileGoalRepository profileGoalRepository;
	private final ProfileRecommendationImpressionRepository impressionRepository;
	private final ProfileInteractionEventRepository interactionEventRepository;
	private final ProfileBlockRepository blockRepository;

	@Transactional(propagation = Propagation.MANDATORY)
	public void anonymizeForDeletedUser(UUID userId) {
		profileRepository.findByUserIdForUpdate(userId).ifPresent(profile -> {
			UUID profileId = profile.getId();
			deletePrivateRelations(profileId);
			anonymize(profile);
			profileRepository.saveAndFlush(profile);
		});
	}

	private void deletePrivateRelations(UUID profileId) {
		profileSkillRepository.deleteAllByProfileId(profileId);
		profileInterestRepository.deleteAllByProfileId(profileId);
		profileGoalRepository.deleteAllByProfileId(profileId);
		impressionRepository.deleteAllBySourceProfileIdOrTargetProfileId(
				profileId,
				profileId
		);
		interactionEventRepository.deleteAllBySourceProfileIdOrTargetProfileId(
				profileId,
				profileId
		);
		blockRepository.deleteAllByBlockerProfileIdOrBlockedProfileId(
				profileId,
				profileId
		);
	}

	private void anonymize(Profile profile) {
		profile.setDisplayName(DELETED_DISPLAY_NAME);
		profile.setBio(null);
		profile.setTelegram(null);
		profile.setFaculty(null);
		profile.setStudyProgram(null);
		profile.setCourse(null);
		profile.setStatus(ProfileStatus.DELETED);
		profile.setDeletedAt(LocalDateTime.now());
		profile.setUser(null);
	}
}
