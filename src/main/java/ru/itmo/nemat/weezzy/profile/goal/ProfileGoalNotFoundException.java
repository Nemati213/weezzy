package ru.itmo.nemat.weezzy.profile.goal;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class ProfileGoalNotFoundException extends NotFoundException {
	public ProfileGoalNotFoundException(UUID profileId, UUID goalId) {
		super("Profile goal link not found: profileId=%s, goalId=%s".formatted(profileId, goalId));
	}
}
