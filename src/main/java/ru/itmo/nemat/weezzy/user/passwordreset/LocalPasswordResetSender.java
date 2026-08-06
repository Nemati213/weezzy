package ru.itmo.nemat.weezzy.user.passwordreset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class LocalPasswordResetSender implements PasswordResetSender {
	private static final Logger log = LoggerFactory.getLogger(
			LocalPasswordResetSender.class
	);
	private static final Map<String, String> TOKENS_BY_EMAIL = new ConcurrentHashMap<>();

	private final PasswordResetLinkFactory linkFactory;

	public LocalPasswordResetSender(PasswordResetLinkFactory linkFactory) {
		this.linkFactory = linkFactory;
	}

	@Override
	public void sendPasswordResetEmail(
			String email,
			String resetToken,
			LocalDateTime expiresAt
	) {
		TOKENS_BY_EMAIL.put(email, resetToken);
		log.info(
				"Local password reset email to {}: link={}, expiresAt={}",
				email,
				linkFactory.create(resetToken),
				expiresAt
		);
	}

	public static Optional<String> takeToken(String email) {
		return Optional.ofNullable(TOKENS_BY_EMAIL.remove(email));
	}
}
