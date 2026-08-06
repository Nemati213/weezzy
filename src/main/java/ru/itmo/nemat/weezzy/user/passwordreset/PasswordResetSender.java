package ru.itmo.nemat.weezzy.user.passwordreset;

import java.time.LocalDateTime;

public interface PasswordResetSender {
	void sendPasswordResetEmail(
			String email,
			String resetToken,
			LocalDateTime expiresAt
	);
}
