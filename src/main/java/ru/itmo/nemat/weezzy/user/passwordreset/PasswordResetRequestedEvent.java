package ru.itmo.nemat.weezzy.user.passwordreset;

import java.time.LocalDateTime;

public record PasswordResetRequestedEvent(
		String email,
		String resetToken,
		LocalDateTime expiresAt
) {
}
