package ru.itmo.nemat.weezzy.common.exception;

public class UnauthorizedException extends RuntimeException {
	public UnauthorizedException(String message) {
		super(message);
	}
}
