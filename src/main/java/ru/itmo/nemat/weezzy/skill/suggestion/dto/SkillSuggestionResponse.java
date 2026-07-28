package ru.itmo.nemat.weezzy.skill.suggestion.dto;

import ru.itmo.nemat.weezzy.skill.suggestion.SkillSuggestion;
import ru.itmo.nemat.weezzy.skill.suggestion.SkillSuggestionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record SkillSuggestionResponse(
		UUID id,
		UUID suggestedByUserId,
		String name,
		String description,
		SkillSuggestionStatus status,
		LocalDateTime createdAt,
		LocalDateTime reviewedAt,
		UUID reviewedByUserId
) {
	public static SkillSuggestionResponse from(SkillSuggestion suggestion) {
		return new SkillSuggestionResponse(
				suggestion.getId(),
				suggestion.getSuggestedBy().getId(),
				suggestion.getName(),
				suggestion.getDescription(),
				suggestion.getStatus(),
				suggestion.getCreatedAt(),
				suggestion.getReviewedAt(),
				suggestion.getReviewedBy() == null ? null : suggestion.getReviewedBy().getId()
		);
	}
}
