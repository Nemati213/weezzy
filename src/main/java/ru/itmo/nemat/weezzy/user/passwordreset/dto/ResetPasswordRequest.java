package ru.itmo.nemat.weezzy.user.passwordreset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
		@NotBlank
		@Size(max = 512)
		String token,

		@NotBlank
		@Size(min = 8, max = 72)
		String newPassword
) {
}
