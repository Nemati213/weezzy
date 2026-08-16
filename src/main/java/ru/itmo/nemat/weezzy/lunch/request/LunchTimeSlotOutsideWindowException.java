package ru.itmo.nemat.weezzy.lunch.request;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

import java.time.LocalDateTime;

public class LunchTimeSlotOutsideWindowException extends BadRequestException {
	public LunchTimeSlotOutsideWindowException(LocalDateTime timeSlot) {
		super("Lunch time slot is outside the available window: " + timeSlot);
	}
}
