package ru.itmo.nemat.weezzy.team.application;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

import java.time.LocalDateTime;

public class TeamApplicationCooldownException extends ConflictException {
	public TeamApplicationCooldownException(LocalDateTime retryAt) {
		super("Reapplying for this team position is unavailable until " + retryAt);
	}
}
