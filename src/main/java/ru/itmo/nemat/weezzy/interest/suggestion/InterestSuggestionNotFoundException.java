package ru.itmo.nemat.weezzy.interest.suggestion;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class InterestSuggestionNotFoundException extends NotFoundException {
	public InterestSuggestionNotFoundException(UUID id) {
		super("Interest suggestion not found: " + id);
	}
}
