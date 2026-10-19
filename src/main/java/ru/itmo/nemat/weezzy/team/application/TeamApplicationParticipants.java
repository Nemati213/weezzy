package ru.itmo.nemat.weezzy.team.application;

import java.util.UUID;

public record TeamApplicationParticipants(
		UUID requestId, UUID ownerProfileId, UUID applicantProfileId
) {
}
