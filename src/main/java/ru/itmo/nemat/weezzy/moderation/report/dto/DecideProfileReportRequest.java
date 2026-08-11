package ru.itmo.nemat.weezzy.moderation.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.itmo.nemat.weezzy.moderation.report.ProfileReportStatus;

public record DecideProfileReportRequest(
		@NotNull
		ProfileReportStatus status,

		@NotBlank
		@Size(max = 1000)
		String decision
) {
}
