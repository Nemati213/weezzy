package ru.itmo.nemat.weezzy.team.request.dto;

import ru.itmo.nemat.weezzy.team.request.TeamRequest;
import ru.itmo.nemat.weezzy.team.request.TeamRequestStatus;
import ru.itmo.nemat.weezzy.team.request.position.dto.TeamRequestPositionResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TeamRequestResponse(
		UUID id,
		UUID teamId,
		String title,
		String description,
		TeamRequestStatus status,
		LocalDateTime recruitmentDeadline,
		long availableTeamSlotsCount,
		List<TeamRequestPositionResponse> positions,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
	public static TeamRequestResponse from(
			TeamRequest request, List<TeamRequestPositionResponse> positions
	) {
		return new TeamRequestResponse(
				request.getId(),
				request.getTeam().getId(),
				request.getTitle(),
				request.getDescription(),
				request.getStatus(),
				request.getRecruitmentDeadline(),
				positions.stream().mapToLong(TeamRequestPositionResponse::remainingSlotsCount).sum(),
				List.copyOf(positions),
				request.getCreatedAt(),
				request.getUpdatedAt()
		);
	}
}
