package ru.itmo.nemat.weezzy.team.dto;

import ru.itmo.nemat.weezzy.team.Team;
import ru.itmo.nemat.weezzy.team.TeamStatus;
import ru.itmo.nemat.weezzy.team.position.dto.TeamPositionResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TeamResponse(
		UUID id,
		String name,
		String purpose,
		String description,
		UUID ownerProfileId,
		TeamStatus status,
		List<TeamMemberResponse> members,
		List<TeamPositionResponse> positions,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static TeamResponse from(
			Team team, List<TeamMemberResponse> members, List<TeamPositionResponse> positions
	) {
		return new TeamResponse(
				team.getId(),
				team.getName(),
				team.getPurpose(),
				team.getDescription(),
				team.getOwnerProfile().getId(),
				team.getStatus(),
				List.copyOf(members),
				List.copyOf(positions),
				team.getCreatedAt(),
				team.getUpdatedAt()
		);
	}
}
