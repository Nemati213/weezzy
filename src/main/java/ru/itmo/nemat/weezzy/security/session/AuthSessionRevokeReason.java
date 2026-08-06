package ru.itmo.nemat.weezzy.security.session;

public enum AuthSessionRevokeReason {
	LOGOUT,
	LOGOUT_ALL,
	TOKEN_REUSE,
	EXPIRED
}
