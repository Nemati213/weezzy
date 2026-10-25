package ru.itmo.nemat.weezzy.team.recommendation.dto;

import ru.itmo.nemat.weezzy.team.Team;
import ru.itmo.nemat.weezzy.team.dto.TeamMemberResponse;

import java.util.List;
import java.util.UUID;

public record TeamRecommendationTeamResponse(
		UUID id, String name, String purpose, String description,
		UUID ownerProfileId, List<TeamMemberResponse> members
) {
	public TeamRecommendationTeamResponse {
		members = List.copyOf(members);
	}

	public static TeamRecommendationTeamResponse from(Team team, List<TeamMemberResponse> members) {
		return new TeamRecommendationTeamResponse(team.getId(), team.getName(),
				team.getPurpose(), team.getDescription(), team.getOwnerProfile().getId(), members);
	}
}
