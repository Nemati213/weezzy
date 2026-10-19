package ru.itmo.nemat.weezzy.team.request.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record UpdateRecruitmentRequest(
		@NotBlank @Size(max = 120) String title,
		@Size(max = 2000) String description,
		@NotNull @Future LocalDateTime recruitmentDeadline
) {
}
