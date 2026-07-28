package ru.itmo.nemat.weezzy.interest.suggestion.dto;

import ru.itmo.nemat.weezzy.interest.suggestion.InterestSuggestion;
import ru.itmo.nemat.weezzy.interest.suggestion.InterestSuggestionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record InterestSuggestionResponse(
		UUID id,
		UUID suggestedByUserId,
		String name,
		String description,
		InterestSuggestionStatus status,
		LocalDateTime createdAt,
		LocalDateTime reviewedAt,
		UUID reviewedByUserId
) {
	public static InterestSuggestionResponse from(InterestSuggestion suggestion) {
		return new InterestSuggestionResponse(
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
