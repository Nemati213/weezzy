package ru.itmo.nemat.weezzy.recommendation.dto;

import ru.itmo.nemat.weezzy.profile.dto.ProfileResponse;

import java.util.List;

public record ProfileRecommendationResponse(
		ProfileResponse profile,
		int score,
		List<String> matchedSkills,
		List<String> matchedInterests,
		List<String> matchedGoals
) {

}
