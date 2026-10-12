package ru.itmo.nemat.weezzy.team.invitation;

import java.util.UUID;

public record TeamInvitationParticipants(
		UUID teamId,
		UUID inviterProfileId,
		UUID inviteeProfileId
) {
}
