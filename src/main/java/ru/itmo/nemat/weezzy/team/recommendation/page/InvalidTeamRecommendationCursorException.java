package ru.itmo.nemat.weezzy.team.recommendation.page;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class InvalidTeamRecommendationCursorException extends BadRequestException {
	public InvalidTeamRecommendationCursorException() {
		super("Invalid team recommendation cursor");
	}
}
