package ru.itmo.nemat.weezzy.profile.goal;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class ProfileGoalAlreadyExistsException extends ConflictException {
	public ProfileGoalAlreadyExistsException(UUID profileId, UUID goalId) {
		super("Profile already has goal: profileId=%s, goalId=%s".formatted(profileId, goalId));
	}
}
