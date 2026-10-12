package ru.itmo.nemat.weezzy.team.invitation.dto;

import ru.itmo.nemat.weezzy.team.invitation.TeamInvitation;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TeamInvitationResponse(
		UUID id,
		UUID teamId,
		TeamInvitationStatus status,
		LocalDateTime createdAt,
		LocalDateTime resolvedAt
) {
	public static TeamInvitationResponse from(TeamInvitation invitation) {
		return new TeamInvitationResponse(
				invitation.getId(), invitation.getTeam().getId(),
				invitation.getStatus(),
				invitation.getCreatedAt(), invitation.getResolvedAt()
		);
	}
}
