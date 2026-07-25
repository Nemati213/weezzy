package ru.itmo.nemat.weezzy.profile.interest;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class ProfileInterestNotFoundException extends NotFoundException {
	public ProfileInterestNotFoundException(UUID profileId, UUID interestId) {
		super("Profile interest link not found: profileId=" + profileId + ", interestId=" + interestId);
	}
}
