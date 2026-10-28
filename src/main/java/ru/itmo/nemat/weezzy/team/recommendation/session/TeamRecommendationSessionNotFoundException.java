package ru.itmo.nemat.weezzy.team.recommendation.session;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

public class TeamRecommendationSessionNotFoundException extends NotFoundException {
	public TeamRecommendationSessionNotFoundException() {
		super("Team recommendation session not found");
	}
}
