package ru.itmo.nemat.weezzy.team.recommendation.session;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Service
@Validated
@RequiredArgsConstructor
public class TeamRecommendationSessionService {
	private static final int MAX_ATTEMPTS = 3;
	private final TeamRecommendationSessionCreator creator;

	@Transactional(propagation = Propagation.NEVER)
	public TeamRecommendationSessionResult createForUser(
			@NotNull UUID userId, @NotNull UUID clientRequestId, @Min(1) @Max(50) int pageSize
	) {
		for (int attempt = 1; ; attempt++) {
			try {
				return creator.create(userId, clientRequestId, pageSize);
			} catch (TransientDataAccessException exception) {
				if (attempt == MAX_ATTEMPTS) {
					throw exception;
				}
			}
		}
	}
}
