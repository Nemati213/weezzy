package ru.itmo.nemat.weezzy.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Set;
import java.util.UUID;

public record RecommendationFilter(
		String faculty,
		String studyProgram,
		Set<@Min(1) @Max(6) Integer> courses,
		Set<UUID> skillIds,
		Set<UUID> interestIds,
		Set<UUID> goalIds
) {
	public RecommendationFilter {
		faculty = normalize(faculty);
		studyProgram = normalize(studyProgram);
		courses = immutableSet(courses);
		skillIds = immutableSet(skillIds);
		interestIds = immutableSet(interestIds);
		goalIds = immutableSet(goalIds);
	}

	private static String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static <T> Set<T> immutableSet(Set<T> values) {
		return values == null ? Set.of() : Set.copyOf(values);
	}
}
