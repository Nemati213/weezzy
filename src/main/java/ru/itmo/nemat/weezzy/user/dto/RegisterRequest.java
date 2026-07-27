package ru.itmo.nemat.weezzy.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank
		@Email
		@Size(max = 320)
		String email,

		@NotBlank
		@Size(min = 8, max = 72)
		String password
) {
}
