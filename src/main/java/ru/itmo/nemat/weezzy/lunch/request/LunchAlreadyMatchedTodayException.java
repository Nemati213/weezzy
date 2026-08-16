package ru.itmo.nemat.weezzy.lunch.request;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.time.LocalDate;
import java.util.UUID;

public class LunchAlreadyMatchedTodayException extends ConflictException {
	public LunchAlreadyMatchedTodayException(UUID profileId, LocalDate date) {
		super("Profile " + profileId + " already has a lunch match on " + date);
	}
}
