package ru.itmo.nemat.weezzy.team.recommendation.impression;

import ru.itmo.nemat.weezzy.common.exception.BadRequestException;

public class DuplicateTeamRecommendationImpressionException extends BadRequestException {
	public DuplicateTeamRecommendationImpressionException() {
		super("entryIds must be distinct");
	}
}
