package ru.itmo.nemat.weezzy.user.dto;

public record RegistrationResponse(
		String email,
		boolean verificationRequired
) {
}
