package ru.itmo.nemat.weezzy.user.emailverification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("production")
public class SmtpEmailVerificationSender implements EmailVerificationSender {
	private final JavaMailSender mailSender;
	private final EmailVerificationLinkFactory linkFactory;
	private final String fromAddress;

	public SmtpEmailVerificationSender(
			JavaMailSender mailSender,
			EmailVerificationLinkFactory linkFactory,
			@Value("${app.mail.from}") String fromAddress
	) {
		this.mailSender = mailSender;
		this.linkFactory = linkFactory;
		this.fromAddress = fromAddress;
	}

	@Override
	public void sendVerificationEmail(
			String email,
			String verificationToken,
			LocalDateTime expiresAt
	) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(fromAddress);
		message.setTo(email);
		message.setSubject("Confirm your Weezzy email");
		message.setText("""
				Confirm your email by opening this link:
				%s

				The link expires at %s.
				If you did not create a Weezzy account, ignore this message.
				""".formatted(linkFactory.create(verificationToken), expiresAt));
		mailSender.send(message);
	}
}
