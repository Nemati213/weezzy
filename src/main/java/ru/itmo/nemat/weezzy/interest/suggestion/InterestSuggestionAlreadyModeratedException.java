package ru.itmo.nemat.weezzy.interest.suggestion;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class InterestSuggestionAlreadyModeratedException extends ConflictException {
	public InterestSuggestionAlreadyModeratedException(UUID id) {
		super("Interest suggestion already moderated: " + id);
	}
}
