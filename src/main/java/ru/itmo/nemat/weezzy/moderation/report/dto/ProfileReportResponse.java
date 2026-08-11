package ru.itmo.nemat.weezzy.moderation.report.dto;

import ru.itmo.nemat.weezzy.moderation.report.ProfileReport;
import ru.itmo.nemat.weezzy.moderation.report.ProfileReportReason;
import ru.itmo.nemat.weezzy.moderation.report.ProfileReportStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProfileReportResponse(
		UUID id,
		UUID reporterProfileId,
		UUID targetProfileId,
		ProfileReportReason reason,
		String comment,
		ProfileReportStatus status,
		String decision,
		UUID reviewedByUserId,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		LocalDateTime reviewedAt,
		LocalDateTime closedAt
) {
	public static ProfileReportResponse from(ProfileReport report) {
		return new ProfileReportResponse(
				report.getId(),
				report.getReporterProfile().getId(),
				report.getTargetProfile().getId(),
				report.getReason(),
				report.getComment(),
				report.getStatus(),
				report.getDecision(),
				report.getReviewedBy() == null
						? null
						: report.getReviewedBy().getId(),
				report.getCreatedAt(),
				report.getUpdatedAt(),
				report.getReviewedAt(),
				report.getClosedAt()
		);
	}
}
