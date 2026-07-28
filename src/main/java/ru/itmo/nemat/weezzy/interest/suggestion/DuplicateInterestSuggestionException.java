package ru.itmo.nemat.weezzy.interest.suggestion;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class DuplicateInterestSuggestionException extends ConflictException {
	public DuplicateInterestSuggestionException(String name) {
		super("Interest suggestion already exists: " + name);
	}
}
