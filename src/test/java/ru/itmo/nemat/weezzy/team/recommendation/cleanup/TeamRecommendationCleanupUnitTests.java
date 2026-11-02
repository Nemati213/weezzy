package ru.itmo.nemat.weezzy.team.recommendation.cleanup;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.itmo.nemat.weezzy.team.recommendation.TeamRecommendationProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TeamRecommendationCleanupUnitTests {
	private static final String PREFIX = "weezzy.team.recommendation.cleanup.";
	private final TeamRecommendationCleanupBatchService batches = mock(TeamRecommendationCleanupBatchService.class);
	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00.123456789Z"), ZoneOffset.UTC);
	private final LocalDateTime now = LocalDateTime.of(2026, 10, 30, 12, 0, 0, 123456000);
	private final TeamRecommendationCleanupResult empty = new TeamRecommendationCleanupResult(0, 0, 0);

	@Test
	void emptyBatchStopsRunAndRecordsSuccessWithoutDeletedRows() {
		when(batches.deleteBatch(any(), any(), anyInt())).thenReturn(empty);
		assertThat(service(7, 10).cleanup()).isEqualTo(empty);
		verify(batches).deleteBatch(now, now.minusDays(30), 7);
		verifyNoMoreInteractions(batches);
		assertThat(counter("runs", "outcome", "success")).isEqualTo(1);
		assertThat(counter("runs", "outcome", "failure")).isZero();
		assertThat(counter("deleted", "kind", "entry")).isZero();
		assertThat(registry.get(PREFIX + "duration").timer().count()).isEqualTo(1);
	}

	@Test
	void partialProgressContinuesUntilEmptyAndCountsEachCommittedKind() {
		when(batches.deleteBatch(any(), any(), anyInt()))
				.thenReturn(new TeamRecommendationCleanupResult(0, 2, 0),
						new TeamRecommendationCleanupResult(1, 1, 3), empty);
		assertThat(service(5, 10).cleanup()).isEqualTo(new TeamRecommendationCleanupResult(1, 3, 3));
		verify(batches, times(3)).deleteBatch(now, now.minusDays(30), 5);
		assertThat(registry.get(PREFIX + "batches").counter().count()).isEqualTo(3);
		assertThat(counter("deleted", "kind", "session")).isEqualTo(1);
		assertThat(counter("deleted", "kind", "entry")).isEqualTo(3);
		assertThat(counter("deleted", "kind", "impression")).isEqualTo(3);
	}

	@Test
	void maximumBatchesBoundsRunEvenWhenMoreRowsRemain() {
		when(batches.deleteBatch(any(), any(), anyInt()))
				.thenReturn(new TeamRecommendationCleanupResult(1, 5, 5));
		assertThat(service(5, 2).cleanup()).isEqualTo(new TeamRecommendationCleanupResult(2, 10, 10));
		verify(batches, times(2)).deleteBatch(now, now.minusDays(30), 5);
		verifyNoMoreInteractions(batches);
	}

	@Test
	void allBatchesUseOneCutoffEvenWhenClockAdvances() {
		Clock advancing = mock(Clock.class);
		when(advancing.getZone()).thenReturn(ZoneOffset.UTC);
		when(advancing.instant()).thenReturn(clock.instant(), clock.instant().plusSeconds(3600));
		when(batches.deleteBatch(any(), any(), anyInt()))
				.thenReturn(new TeamRecommendationCleanupResult(1, 0, 0), empty);
		new TeamRecommendationCleanupService(batches,
				new TeamRecommendationCleanupMetrics(registry), properties(5, 10), advancing).cleanup();
		verify(batches, times(2)).deleteBatch(now, now.minusDays(30), 5);
		verify(advancing).instant();
	}

	@Test
	void failedFirstBatchDoesNotCountDeletesOrSuccessfulBatches() {
		when(batches.deleteBatch(any(), any(), anyInt())).thenThrow(new IllegalStateException("commit failed"));
		assertThatThrownBy(() -> service(5, 10).cleanup()).hasMessage("commit failed");
		assertThat(counter("runs", "outcome", "failure")).isEqualTo(1);
		assertThat(counter("runs", "outcome", "success")).isZero();
		assertThat(registry.get(PREFIX + "batches").counter().count()).isZero();
		assertThat(counter("deleted", "kind", "session")).isZero();
		assertThat(registry.get(PREFIX + "duration").timer().count()).isEqualTo(1);
	}

	@Test
	void laterFailureKeepsCountsOfPreviouslyCommittedBatches() {
		when(batches.deleteBatch(any(), any(), anyInt()))
				.thenReturn(new TeamRecommendationCleanupResult(2, 4, 3))
				.thenThrow(new IllegalStateException("next batch failed"));
		assertThatThrownBy(() -> service(5, 10).cleanup()).hasMessage("next batch failed");
		assertThat(counter("runs", "outcome", "failure")).isEqualTo(1);
		assertThat(registry.get(PREFIX + "batches").counter().count()).isEqualTo(1);
		assertThat(counter("deleted", "kind", "session")).isEqualTo(2);
		assertThat(counter("deleted", "kind", "entry")).isEqualTo(4);
		assertThat(counter("deleted", "kind", "impression")).isEqualTo(3);
	}

	@Test
	void batchLocksParentsBeforeRemovingChildrenAndEmptySessions() {
		var repository = mock(TeamRecommendationCleanupRepository.class);
		var ids = List.of(UUID.randomUUID());
		when(repository.lockExpiredSessions(now, 5)).thenReturn(ids);
		when(repository.deleteEntries(ids, 5)).thenReturn(3);
		when(repository.deleteEmptySessions(ids)).thenReturn(1);
		when(repository.deleteOldImpressions(now.minusDays(30), 5)).thenReturn(2);
		assertThat(new TeamRecommendationCleanupBatchService(repository)
				.deleteBatch(now, now.minusDays(30), 5))
				.isEqualTo(new TeamRecommendationCleanupResult(1, 3, 2));
		var order = inOrder(repository);
		order.verify(repository).lockExpiredSessions(now, 5);
		order.verify(repository).deleteEntries(ids, 5);
		order.verify(repository).deleteEmptySessions(ids);
		order.verify(repository).deleteOldImpressions(now.minusDays(30), 5);
		order.verifyNoMoreInteractions();
	}

	@Test
	void noExpiredSessionsStillAllowsHistoryCleanupWithoutEmptyInQuery() {
		var repository = mock(TeamRecommendationCleanupRepository.class);
		when(repository.deleteOldImpressions(now.minusDays(30), 5)).thenReturn(2);
		assertThat(new TeamRecommendationCleanupBatchService(repository)
				.deleteBatch(now, now.minusDays(30), 5))
				.isEqualTo(new TeamRecommendationCleanupResult(0, 0, 2));
		verify(repository).lockExpiredSessions(now, 5);
		verify(repository).deleteOldImpressions(now.minusDays(30), 5);
		verifyNoMoreInteractions(repository);
	}

	@Test
	void schedulerDelegatesToBoundedService() {
		var service = mock(TeamRecommendationCleanupService.class);
		when(service.cleanup()).thenReturn(empty);
		new TeamRecommendationCleanupScheduler(service).cleanup();
		verify(service).cleanup();
	}

	@Test
	void schedulerCanBeDisabledWithoutRemovingManualCleanupService() {
		var runner = new ApplicationContextRunner()
				.withUserConfiguration(TeamRecommendationCleanupScheduler.class)
				.withBean(TeamRecommendationCleanupService.class, () -> mock(TeamRecommendationCleanupService.class));
		runner.withPropertyValues("app.team-recommendation.cleanup.enabled=false").run(context -> {
			assertThat(context).hasNotFailed().doesNotHaveBean(TeamRecommendationCleanupScheduler.class);
			assertThat(context).hasSingleBean(TeamRecommendationCleanupService.class);
		});
		runner.withPropertyValues("app.team-recommendation.cleanup.enabled=true")
				.run(context -> assertThat(context).hasNotFailed()
						.hasSingleBean(TeamRecommendationCleanupScheduler.class));
	}

	private TeamRecommendationCleanupService service(int size, int maximum) {
		return new TeamRecommendationCleanupService(batches,
				new TeamRecommendationCleanupMetrics(registry), properties(size, maximum), clock);
	}

	private TeamRecommendationProperties properties(int size, int maximum) {
		return new TeamRecommendationProperties(Duration.ofDays(3), Duration.ofHours(24),
				Duration.ofDays(30), 10,
				new TeamRecommendationProperties.Cleanup(true, Duration.ofMinutes(15), size, maximum));
	}

	private double counter(String name, String tag, String value) {
		return registry.get(PREFIX + name).tag(tag, value).counter().count();
	}
}
