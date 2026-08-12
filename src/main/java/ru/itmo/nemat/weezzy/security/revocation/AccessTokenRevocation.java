package ru.itmo.nemat.weezzy.security.revocation;

public record AccessTokenRevocation(
		long revokedBeforeEpochMilli,
		AccessTokenRevocationReason reason
) {
}
