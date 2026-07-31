package ru.itmo.nemat.weezzy.onboarding;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.List;

public class ProfileActivationNotAllowedException extends ConflictException {
	public ProfileActivationNotAllowedException(List<OnboardingStep> missingSteps) {
		super("Profile cannot be activated. Missing steps: " + missingSteps);
	}
}
