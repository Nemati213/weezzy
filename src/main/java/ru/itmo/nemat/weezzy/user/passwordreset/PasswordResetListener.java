package ru.itmo.nemat.weezzy.user.passwordreset;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PasswordResetListener {
	private static final Logger log = LoggerFactory.getLogger(
			PasswordResetListener.class
	);

	private final PasswordResetSender passwordResetSender;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(PasswordResetRequestedEvent event) {
		try {
			passwordResetSender.sendPasswordResetEmail(
					event.email(),
					event.resetToken(),
					event.expiresAt()
			);
		} catch (RuntimeException exception) {
			log.error(
					"Could not send password reset message to {}",
					event.email(),
					exception
			);
		}
	}
}
