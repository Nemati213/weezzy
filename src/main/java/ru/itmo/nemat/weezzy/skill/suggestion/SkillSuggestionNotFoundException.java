package ru.itmo.nemat.weezzy.skill.suggestion;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

import java.util.UUID;

public class SkillSuggestionNotFoundException extends NotFoundException {
    public SkillSuggestionNotFoundException(UUID id) {
        super("Skill suggestion not found for id: " + id);
    }
}
