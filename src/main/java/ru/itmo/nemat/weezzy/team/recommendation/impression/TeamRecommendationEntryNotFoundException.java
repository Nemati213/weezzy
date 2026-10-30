package ru.itmo.nemat.weezzy.team.recommendation.impression;

import ru.itmo.nemat.weezzy.common.exception.NotFoundException;

public class TeamRecommendationEntryNotFoundException extends NotFoundException {
	public TeamRecommendationEntryNotFoundException() {
		super("Team recommendation entry not found");
	}
}
