package ru.itmo.nemat.weezzy.team.recommendation.session;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamRecommendationSessionRetryTests {
	private final TeamRecommendationSessionCreator creator = mock(TeamRecommendationSessionCreator.class);
	private final TeamRecommendationSessionService service = new TeamRecommendationSessionService(creator);
	private final UUID user = UUID.randomUUID();
	private final UUID key = UUID.randomUUID();

	@Test
	void retriesTransientConflictWithTheSameRequest() {
		var result = new TeamRecommendationSessionResult(UUID.randomUUID(), null, null, 20, 0, true);
		when(creator.create(user, key, 20)).thenThrow(new CannotAcquireLockException("retry"))
				.thenReturn(result);
		assertThat(service.createForUser(user, key, 20)).isSameAs(result);
		verify(creator, times(2)).create(user, key, 20);
	}

	@Test
	void limitsRetriesAndPreservesTheFailure() {
		var failure = new CannotAcquireLockException("retry");
		when(creator.create(user, key, 20)).thenThrow(failure);
		assertThatThrownBy(() -> service.createForUser(user, key, 20)).isSameAs(failure);
		verify(creator, times(3)).create(user, key, 20);
	}

	@Test
	void businessConflictsAreNotRetried() {
		when(creator.create(user, key, 20)).thenThrow(new TeamRecommendationSessionConflictException());
		assertThatThrownBy(() -> service.createForUser(user, key, 20))
				.isInstanceOf(TeamRecommendationSessionConflictException.class);
		verify(creator).create(user, key, 20);
	}
}
