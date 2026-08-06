package ru.itmo.nemat.weezzy.user.passwordreset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PasswordResetLinkFactory {
	private final String frontendBaseUrl;

	public PasswordResetLinkFactory(
			@Value("${app.frontend.base-url}") String frontendBaseUrl
	) {
		this.frontendBaseUrl = frontendBaseUrl;
	}

	public String create(String resetToken) {
		return UriComponentsBuilder.fromUriString(frontendBaseUrl)
				.path("/reset-password")
				.queryParam("token", resetToken)
				.build()
				.encode()
				.toUriString();
	}
}
