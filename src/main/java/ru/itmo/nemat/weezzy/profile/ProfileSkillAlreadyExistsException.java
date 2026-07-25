package ru.itmo.nemat.weezzy.profile;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class ProfileSkillAlreadyExistsException extends ConflictException {
	public ProfileSkillAlreadyExistsException(UUID profileId, UUID skillId) {
		super("Profile already has skill: profileId=" + profileId + ", skillId=" + skillId);
	}
}
