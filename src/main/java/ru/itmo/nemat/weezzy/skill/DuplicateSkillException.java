package ru.itmo.nemat.weezzy.skill;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class DuplicateSkillException extends ConflictException {
	public DuplicateSkillException(String name) {
		super("Skill already exists: " + name);
	}
}
