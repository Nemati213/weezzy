package ru.itmo.nemat.weezzy.skill.suggestion;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.util.UUID;

public class SkillSuggestionAlreadyModeratedException extends ConflictException {
    public SkillSuggestionAlreadyModeratedException(UUID id) {
        super("Skill suggestion already moderated for id: " + id);
    }
}
