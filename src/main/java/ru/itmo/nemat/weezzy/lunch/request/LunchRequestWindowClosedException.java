package ru.itmo.nemat.weezzy.lunch.request;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

import java.time.LocalTime;
import java.time.ZoneId;

public class LunchRequestWindowClosedException extends BadRequestException {
	public LunchRequestWindowClosedException(
			LocalTime windowStart,
			LocalTime windowEnd,
			ZoneId zoneId
	) {
		super("Lunch requests are available from " + windowStart
				+ " to " + windowEnd + " in " + zoneId);
	}
}
