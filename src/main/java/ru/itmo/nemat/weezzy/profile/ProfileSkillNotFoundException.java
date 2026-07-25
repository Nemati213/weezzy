package ru.itmo.nemat.weezzy.profile;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class ProfileSkillNotFoundException extends NotFoundException {
	public ProfileSkillNotFoundException(UUID profileId, UUID skillId) {
		super("Profile skill link not found: profileId=" + profileId + ", skillId=" + skillId);
	}
}
