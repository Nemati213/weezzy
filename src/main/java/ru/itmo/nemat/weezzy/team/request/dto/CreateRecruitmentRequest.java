package ru.itmo.nemat.weezzy.team.request.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ru.itmo.nemat.weezzy.team.request.position.dto.CreateRecruitmentPositionRequest;

import java.time.LocalDateTime;
import java.util.List;

public record CreateRecruitmentRequest(
		@NotBlank @Size(max = 120) String title,
		@Size(max = 2000) String description,
		@NotNull @Future LocalDateTime recruitmentDeadline,
		@Positive int targetTeamSize,
		@NotEmpty @Valid List<@NotNull CreateRecruitmentPositionRequest> positions
) {
}
