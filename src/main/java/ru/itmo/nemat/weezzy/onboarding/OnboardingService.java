package ru.itmo.nemat.weezzy.onboarding;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.onboarding.dto.OnboardingResponse;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoal;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoalRepository;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterest;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterestRepository;
import ru.itmo.nemat.weezzy.profile.photo.ProfilePhotoRepository;
import ru.itmo.nemat.weezzy.profile.photo.ProfilePhotoStatus;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkillRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {
	private static final int TOTAL_STEPS = OnboardingStep.values().length;

	private final ProfileRepository profileRepository;
	private final ProfileSkillRepository profileSkillRepository;
	private final ProfileInterestRepository profileInterestRepository;
	private final ProfileGoalRepository profileGoalRepository;
	private final ProfilePhotoRepository profilePhotoRepository;

	public OnboardingResponse findForUser(UUID userId) {
		return profileRepository.findByUserId(userId)
				.map(this::buildResponse)
				.orElseGet(this::buildEmptyResponse);
	}

	public void validateActivationAllowed(Profile profile) {
		List<OnboardingStep> missingSteps = findMissingRequiredSteps(profile);
		if (!missingSteps.isEmpty()) {
			throw new ProfileActivationNotAllowedException(missingSteps);
		}
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void moveToDraftIfIncomplete(Profile profile) {
		if (profile.getStatus() == ProfileStatus.DRAFT
				|| findMissingRequiredSteps(profile).isEmpty()) {
			return;
		}

		profile.setStatus(ProfileStatus.DRAFT);
		profileRepository.save(profile);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void moveToDraftIfIncomplete(Collection<Profile> profiles) {
		profiles.forEach(this::moveToDraftIfIncomplete);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public List<Profile> lockProfilesUsingGoal(UUID goalId) {
		List<UUID> profileIds = profileGoalRepository.findAllByGoalId(goalId).stream()
				.map(ProfileGoal::getProfileId)
				.toList();
		return lockProfiles(profileIds);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public List<Profile> lockProfilesUsingInterest(UUID interestId) {
		List<UUID> profileIds = profileInterestRepository.findAllByInterestId(interestId)
				.stream()
				.map(ProfileInterest::getProfileId)
				.toList();
		return lockProfiles(profileIds);
	}

	private OnboardingResponse buildResponse(Profile profile) {
		List<OnboardingStep> missingSteps = findMissingRequiredSteps(profile);
		boolean activationAllowed = missingSteps.isEmpty();
		if (profile.getStatus() != ProfileStatus.ACTIVE) {
			missingSteps.add(OnboardingStep.ACTIVATION);
		}

		int completedSteps = TOTAL_STEPS - missingSteps.size();
		int progress = completedSteps * 100 / TOTAL_STEPS;
		return new OnboardingResponse(
				profile.getId(),
				profile.getStatus(),
				progress,
				activationAllowed,
				List.copyOf(missingSteps)
		);
	}

	private List<OnboardingStep> findMissingRequiredSteps(Profile profile) {
		List<OnboardingStep> missingSteps = new ArrayList<>();
		UUID profileId = profile.getId();

		if (!isDetailsComplete(profile)) {
			missingSteps.add(OnboardingStep.PROFILE_DETAILS);
		}
		if (!profileSkillRepository.existsByProfileId(profileId)) {
			missingSteps.add(OnboardingStep.SKILLS);
		}
		if (!profileInterestRepository.existsByProfileId(profileId)) {
			missingSteps.add(OnboardingStep.INTERESTS);
		}
		if (!profileGoalRepository.existsByProfileId(profileId)) {
			missingSteps.add(OnboardingStep.GOALS);
		}
		if (!profilePhotoRepository.existsByProfileIdAndStatus(
				profileId,
				ProfilePhotoStatus.READY
		)) {
			missingSteps.add(OnboardingStep.PHOTOS);
		}

		return missingSteps;
	}

	private boolean isDetailsComplete(Profile profile) {
		return isNotBlank(profile.getDisplayName())
				&& isNotBlank(profile.getBio())
				&& isNotBlank(profile.getTelegram())
				&& isNotBlank(profile.getFaculty())
				&& isNotBlank(profile.getStudyProgram())
				&& profile.getCourse() != null;
	}

	private boolean isNotBlank(String value) {
		return value != null && !value.isBlank();
	}

	private List<Profile> lockProfiles(List<UUID> profileIds) {
		return profileIds.isEmpty()
				? List.of()
				: profileRepository.findAllByIdForUpdate(profileIds);
	}

	private OnboardingResponse buildEmptyResponse() {
		return new OnboardingResponse(
				null,
				null,
				0,
				false,
				List.of(
						OnboardingStep.PROFILE_DETAILS,
						OnboardingStep.SKILLS,
						OnboardingStep.INTERESTS,
						OnboardingStep.GOALS,
						OnboardingStep.PHOTOS,
						OnboardingStep.ACTIVATION
				)
		);
	}
}
