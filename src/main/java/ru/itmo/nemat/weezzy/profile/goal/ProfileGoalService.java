package ru.itmo.nemat.weezzy.profile.goal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.goal.Goal;
import ru.itmo.nemat.weezzy.goal.GoalService;
import ru.itmo.nemat.weezzy.onboarding.OnboardingService;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileGoalService {
	private final ProfileService profileService;
	private final GoalService goalService;
	private final ProfileGoalRepository profileGoalRepository;
	private final OnboardingService onboardingService;

	@Transactional
	public Goal addGoal(UUID profileId, UUID goalId) {
		profileService.findByIdForUpdate(profileId);
		Goal goal = goalService.findById(goalId);

		if (profileGoalRepository.existsByProfileIdAndGoalId(profileId, goalId)) {
			throw new ProfileGoalAlreadyExistsException(profileId, goalId);
		}

		ProfileGoal profileGoal = new ProfileGoal();
		profileGoal.setProfileId(profileId);
		profileGoal.setGoalId(goalId);
		profileGoalRepository.save(profileGoal);

		return goal;
	}

	@Transactional(readOnly = true)
	public List<Goal> findGoals(UUID profileId) {
		profileService.findById(profileId);

		return profileGoalRepository.findAllByProfileId(profileId).stream()
				.map(ProfileGoal::getGoalId)
				.map(goalService::findById)
				.toList();
	}

	@Transactional
	public void removeGoal(UUID profileId, UUID goalId) {
		Profile profile = profileService.findByIdForUpdate(profileId);
		goalService.findById(goalId);

		if (!profileGoalRepository.existsByProfileIdAndGoalId(profileId, goalId)) {
			throw new ProfileGoalNotFoundException(profileId, goalId);
		}

		profileGoalRepository.deleteByProfileIdAndGoalId(profileId, goalId);
		profileGoalRepository.flush();
		onboardingService.moveToDraftIfIncomplete(profile);
	}
}
