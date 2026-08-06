package ru.itmo.nemat.weezzy.user.passwordreset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("production")
public class SmtpPasswordResetSender implements PasswordResetSender {
	private final JavaMailSender mailSender;
	private final PasswordResetLinkFactory linkFactory;
	private final String fromAddress;

	public SmtpPasswordResetSender(
			JavaMailSender mailSender,
			PasswordResetLinkFactory linkFactory,
			@Value("${app.mail.from}") String fromAddress
	) {
		this.mailSender = mailSender;
		this.linkFactory = linkFactory;
		this.fromAddress = fromAddress;
	}

	@Override
	public void sendPasswordResetEmail(
			String email,
			String resetToken,
			LocalDateTime expiresAt
	) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(fromAddress);
		message.setTo(email);
		message.setSubject("Reset your Weezzy password");
		message.setText("""
				Reset your password by opening this link:
				%s

				The link expires at %s.
				If you did not request a password reset, ignore this message.
				""".formatted(linkFactory.create(resetToken), expiresAt));
		mailSender.send(message);
	}
}
