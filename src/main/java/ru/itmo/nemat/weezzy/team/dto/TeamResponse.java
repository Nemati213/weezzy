package ru.itmo.nemat.weezzy.team.dto;

import ru.itmo.nemat.weezzy.team.Team;
import ru.itmo.nemat.weezzy.team.TeamStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TeamResponse(
		UUID id,
		String name,
		String description,
		UUID ownerProfileId,
		TeamStatus status,
		List<TeamMemberResponse> members,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static TeamResponse from(Team team, List<TeamMemberResponse> members) {
		return new TeamResponse(
				team.getId(),
				team.getName(),
				team.getDescription(),
				team.getOwnerProfile().getId(),
				team.getStatus(),
				List.copyOf(members),
				team.getCreatedAt(),
				team.getUpdatedAt()
		);
	}
}
