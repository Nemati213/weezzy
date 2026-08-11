package ru.itmo.nemat.weezzy.moderation.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.itmo.nemat.weezzy.moderation.report.ProfileReportReason;

public record CreateProfileReportRequest(
		@NotNull
		ProfileReportReason reason,

		@Size(max = 1000)
		String comment
) {
}
