package ru.itmo.nemat.weezzy.user.emailverification;

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
public class LocalEmailVerificationSender implements EmailVerificationSender {
	private static final Logger log = LoggerFactory.getLogger(
			LocalEmailVerificationSender.class
	);
	private static final Map<String, String> TOKENS_BY_EMAIL = new ConcurrentHashMap<>();

	private final EmailVerificationLinkFactory linkFactory;

	public LocalEmailVerificationSender(EmailVerificationLinkFactory linkFactory) {
		this.linkFactory = linkFactory;
	}

	@Override
	public void sendVerificationEmail(
			String email,
			String verificationToken,
			LocalDateTime expiresAt
	) {
		TOKENS_BY_EMAIL.put(email, verificationToken);
		log.info(
				"Local verification email to {}: link={}, expiresAt={}",
				email,
				linkFactory.create(verificationToken),
				expiresAt
		);
	}

	public static Optional<String> takeToken(String email) {
		return Optional.ofNullable(TOKENS_BY_EMAIL.remove(email));
	}
}
