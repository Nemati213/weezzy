package ru.itmo.nemat.weezzy.team.recommendation.session;

import ru.itmo.nemat.weezzy.common.exception.ConflictException;

public class TeamRecommendationSessionConflictException extends ConflictException {
	public TeamRecommendationSessionConflictException() {
		super("This clientRequestId was already used with a different pageSize");
	}
}
