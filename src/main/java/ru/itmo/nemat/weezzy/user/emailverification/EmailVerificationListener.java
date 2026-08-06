package ru.itmo.nemat.weezzy.user.emailverification;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EmailVerificationListener {
	private static final Logger log = LoggerFactory.getLogger(
			EmailVerificationListener.class
	);

	private final EmailVerificationSender emailVerificationSender;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(EmailVerificationRequestedEvent event) {
		try {
			emailVerificationSender.sendVerificationEmail(
					event.email(),
					event.verificationToken(),
					event.expiresAt()
			);
		} catch (RuntimeException exception) {
			log.error(
					"Could not send email verification message to {}",
					event.email(),
					exception
			);
		}
	}
}
