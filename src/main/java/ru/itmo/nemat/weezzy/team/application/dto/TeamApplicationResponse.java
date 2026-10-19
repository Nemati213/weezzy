package ru.itmo.nemat.weezzy.team.application.dto;

import ru.itmo.nemat.weezzy.team.application.TeamApplication;
import ru.itmo.nemat.weezzy.team.application.TeamApplicationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TeamApplicationResponse(
		UUID id,
		UUID teamId,
		UUID requestId,
		UUID positionId,
		UUID teamPositionId,
		UUID applicantProfileId,
		String message,
		TeamApplicationStatus status,
		LocalDateTime createdAt,
		LocalDateTime resolvedAt
) {
	public static TeamApplicationResponse from(TeamApplication application) {
		var position = application.getPosition();
		return new TeamApplicationResponse(
				application.getId(), position.getRequest().getTeam().getId(),
				position.getRequest().getId(), position.getId(),
				position.getPosition().getId(),
				application.getApplicantProfile().getId(), application.getMessage(),
				application.getStatus(), application.getCreatedAt(), application.getResolvedAt()
		);
	}
}
