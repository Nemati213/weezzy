package ru.itmo.nemat.weezzy.user.emailverification;

import java.time.LocalDateTime;

public interface EmailVerificationSender {
	void sendVerificationEmail(
			String email,
			String verificationToken,
			LocalDateTime expiresAt
	);
}
