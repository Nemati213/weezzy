package ru.itmo.nemat.weezzy.moderation.sanction.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import ru.itmo.nemat.weezzy.moderation.sanction.AccountSanctionType;

import java.time.LocalDateTime;

@Component
@Profile("production")
public class SmtpAccountSanctionEmailSender implements AccountSanctionEmailSender {
	private final JavaMailSender mailSender;
	private final String fromAddress;

	public SmtpAccountSanctionEmailSender(
			JavaMailSender mailSender,
			@Value("${app.mail.from}") String fromAddress
	) {
		this.mailSender = mailSender;
		this.fromAddress = fromAddress;
	}

	@Override
	public void sendSanctionCreated(
			String email,
			AccountSanctionType type,
			String reason,
			LocalDateTime expiresAt
	) {
		String restriction = switch (type) {
			case PERMANENT_BAN -> "Your Weezzy account has been permanently blocked.";
			case TEMPORARY_SUSPENSION ->
					"Your Weezzy account has been suspended until %s.".formatted(expiresAt);
		};
		send(email, "Your Weezzy account has been restricted", """
				%s

				Reason: %s
				""".formatted(restriction, reason));
	}

	@Override
	public void sendSanctionRevoked(String email, String revocationReason) {
		send(email, "Your Weezzy account restriction has been revoked", """
				The restriction on your Weezzy account has been revoked.

				Reason: %s
				""".formatted(revocationReason));
	}

	private void send(String email, String subject, String text) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(fromAddress);
		message.setTo(email);
		message.setSubject(subject);
		message.setText(text);
		mailSender.send(message);
	}
}
