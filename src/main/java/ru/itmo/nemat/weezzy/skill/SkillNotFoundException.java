package ru.itmo.nemat.weezzy.skill;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class SkillNotFoundException extends NotFoundException {
	public SkillNotFoundException(UUID id) {
		super("Skill not found: " + id);
	}
}
