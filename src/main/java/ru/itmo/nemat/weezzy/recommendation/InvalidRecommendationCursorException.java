package ru.itmo.nemat.weezzy.recommendation;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidRecommendationCursorException extends BadRequestException {
	public InvalidRecommendationCursorException() {
		super("Invalid recommendation cursor");
	}
}
