package ru.itmo.nemat.weezzy.team.recommendation.dto;

import ru.itmo.nemat.weezzy.team.request.TeamRequest;

import java.time.LocalDateTime;
import java.util.UUID;

public record TeamRecommendationRequestResponse(
		UUID id, String title, String description, LocalDateTime recruitmentDeadline
) {
	public static TeamRecommendationRequestResponse from(TeamRequest request) {
		return new TeamRecommendationRequestResponse(request.getId(), request.getTitle(),
				request.getDescription(), request.getRecruitmentDeadline());
	}
}
