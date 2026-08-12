package ru.itmo.nemat.weezzy.moderation.sanction.dto;

import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanction;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionStatus;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccountSanctionResponse(
		UUID id,
		UUID targetUserId,
		UUID targetProfileId,
		UUID sourceReportId,
		AccountSanctionType type,
		AccountSanctionStatus status,
		String reason,
		LocalDateTime expiresAt,
		UUID createdByUserId,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		LocalDateTime revokedAt,
		UUID revokedByUserId,
		String revocationReason
) {
	public static AccountSanctionResponse from(AccountSanction sanction) {
		return new AccountSanctionResponse(
				sanction.getId(),
				sanction.getTargetUserId(),
				sanction.getTargetProfileId(),
				sanction.getSourceReportId(),
				sanction.getType(),
				sanction.getStatus(),
				sanction.getReason(),
				sanction.getExpiresAt(),
				sanction.getCreatedByUserId(),
				sanction.getCreatedAt(),
				sanction.getUpdatedAt(),
				sanction.getRevokedAt(),
				sanction.getRevokedByUserId(),
				sanction.getRevocationReason()
		);
	}
}
