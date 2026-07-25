package ru.itmo.nemat.weezzy.profile.interest;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class ProfileInterestAlreadyExistsException extends ConflictException {
	public ProfileInterestAlreadyExistsException(UUID profileId, UUID interestId) {
		super("Profile already has interest: profileId=" + profileId + ", interestId=" + interestId);
	}
}
