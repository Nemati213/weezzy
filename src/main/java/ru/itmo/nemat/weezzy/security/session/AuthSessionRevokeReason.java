package ru.itmo.nemat.weezzy.security.session;

public enum AuthSessionRevokeReason {
	LOGOUT,
	LOGOUT_ALL,
	PASSWORD_RESET,
	TOKEN_REUSE,
	EXPIRED,
	ACCOUNT_SANCTION
}
