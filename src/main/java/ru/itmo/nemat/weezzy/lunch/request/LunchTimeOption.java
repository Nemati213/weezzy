package ru.itmo.nemat.weezzy.lunch.request;

import java.time.Duration;

public enum LunchTimeOption {
	NOW(Duration.ZERO),
	IN_30_MINUTES(Duration.ofMinutes(30)),
	IN_1_HOUR(Duration.ofHours(1));

	private final Duration offset;

	LunchTimeOption(Duration offset) {
		this.offset = offset;
	}

	public Duration offset() {
		return offset;
	}
}
