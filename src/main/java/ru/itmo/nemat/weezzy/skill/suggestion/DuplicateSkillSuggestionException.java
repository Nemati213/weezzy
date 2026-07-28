package ru.itmo.nemat.weezzy.skill.suggestion;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class DuplicateSkillSuggestionException extends ConflictException {
	public DuplicateSkillSuggestionException(String name) {
		super("Skill suggestion already exists: " + name);
	}
}
