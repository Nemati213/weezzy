package ru.itmo.nemat.weezzy.user.emailverification;

import java.time.LocalDateTime;

public record EmailVerificationRequestedEvent(
		String email,
		String verificationToken,
		LocalDateTime expiresAt
) {
}
