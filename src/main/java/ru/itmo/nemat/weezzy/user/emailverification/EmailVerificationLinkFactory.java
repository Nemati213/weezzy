package ru.itmo.nemat.weezzy.user.emailverification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class EmailVerificationLinkFactory {
	private final String frontendBaseUrl;

	public EmailVerificationLinkFactory(
			@Value("${app.frontend.base-url}") String frontendBaseUrl
	) {
		this.frontendBaseUrl = frontendBaseUrl;
	}

	public String create(String verificationToken) {
		return UriComponentsBuilder.fromUriString(frontendBaseUrl)
				.path("/verify-email")
				.queryParam("token", verificationToken)
				.build()
				.encode()
				.toUriString();
	}
}
