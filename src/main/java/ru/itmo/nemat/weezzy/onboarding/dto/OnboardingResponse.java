package ru.itmo.nemat.weezzy.onboarding.dto;

import ru.itmo.nemat.weezzy.onboarding.OnboardingStep;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;

import java.util.List;
import java.util.UUID;

public record OnboardingResponse(
		UUID profileId,
		ProfileStatus profileStatus,
		int progress,
		boolean activationAllowed,
		List<OnboardingStep> missingSteps
) {
}
