package ru.itmo.nemat.weezzy.profile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 80)
        @Pattern(regexp = ".*\\S.*")
        String displayName,

        @Size(max = 500)
        String bio,

        @Size(max = 64)
        String telegram,

        @Size(max = 120)
        String faculty,

        @Size(max = 160)
        String studyProgram,

        @Min(1)
        @Max(6)
        Integer course
) {
}
