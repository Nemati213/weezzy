package ru.itmo.nemat.weezzy.lunch.matching;

import org.junit.jupiter.api.Test;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LunchMatchingPipelineTests {
	private static final UUID LOCATION_ID = new UUID(10, 10);
	private static final LocalDateTime TIME_SLOT = LocalDateTime.of(
			2026,
			8,
			22,
			13,
			0
	);
	private static final LocalDateTime CREATED_AT = LocalDateTime.of(
			2026,
			8,
			22,
			12,
			0
	);

	private final LunchMatchingPipeline pipeline = new LunchMatchingPipeline(List.of(
			new DesperatePairStrategy(),
			new MixedTopicStrategy(),
			new StrictTopicStrategy()
	));

	@Test
	void strictTopicMaximizesMatchedCandidates() {
		List<MatchingCandidate> fiveCandidates = candidates(5, LunchTopic.STUDY);

		MatchingPipelineResult fiveResult = pipeline.match(
				bucket(fiveCandidates),
				TIME_SLOT.minusMinutes(10)
		);

		assertThat(fiveResult.groups()).hasSize(1);
		assertThat(fiveResult.groups().getFirst().topic())
				.isEqualTo(LunchTopic.STUDY);
		assertThat(fiveResult.groups().getFirst().candidates()).hasSize(4);
		assertThat(fiveResult.remainingCandidates())
				.containsExactly(fiveCandidates.getLast());

		List<MatchingCandidate> elevenCandidates = candidates(11, LunchTopic.STARTUPS);
		MatchingPipelineResult elevenResult = pipeline.match(
				bucket(elevenCandidates.reversed()),
				TIME_SLOT.minusMinutes(10)
		);

		assertThat(elevenResult.groups())
				.extracting(group -> group.candidates().size())
				.containsExactly(4, 4, 3);
		assertThat(elevenResult.groups())
				.extracting(MatchingGroup::topic)
				.containsOnly(LunchTopic.STARTUPS);
		assertThat(elevenResult.remainingCandidates()).isEmpty();
	}

	@Test
	void pipelinePassesOnlyUnmatchedCandidatesToTheNextStrategy() {
		List<MatchingCandidate> candidates = List.of(
				candidate(1, LunchTopic.STUDY),
				candidate(2, LunchTopic.STUDY),
				candidate(3, LunchTopic.STUDY),
				candidate(4, LunchTopic.STUDY),
				candidate(5, LunchTopic.STARTUPS),
				candidate(6, LunchTopic.IT_CAREER),
				candidate(7, LunchTopic.NETWORKING)
		);

		MatchingPipelineResult result = pipeline.match(
				bucket(candidates.reversed()),
				TIME_SLOT.minusMinutes(10)
		);

		assertThat(result.groups()).hasSize(2);
		assertThat(result.groups().get(0).topic()).isEqualTo(LunchTopic.STUDY);
		assertThat(result.groups().get(0).requestIds())
				.containsExactlyElementsOf(requestIds(candidates.subList(0, 4)));
		assertThat(result.groups().get(1).topic())
				.isEqualTo(LunchTopic.CASUAL_CHAT);
		assertThat(result.groups().get(1).requestIds())
				.containsExactlyElementsOf(requestIds(candidates.subList(4, 7)));
		assertThat(result.groups().stream()
				.flatMap(group -> group.requestIds().stream()))
				.doesNotHaveDuplicates();
		assertThat(result.remainingCandidates()).isEmpty();
	}

	@Test
	void mixedTopicMaximizesGroupsAndAssignsCasualTopic() {
		List<MatchingCandidate> candidates = List.of(
				candidate(1, LunchTopic.STUDY),
				candidate(2, LunchTopic.STUDY),
				candidate(3, LunchTopic.STARTUPS),
				candidate(4, LunchTopic.STARTUPS),
				candidate(5, LunchTopic.IT_CAREER),
				candidate(6, LunchTopic.IT_CAREER),
				candidate(7, LunchTopic.NETWORKING),
				candidate(8, LunchTopic.NETWORKING)
		);

		MatchingPipelineResult result = pipeline.match(
				bucket(candidates.reversed()),
				TIME_SLOT.minusMinutes(10)
		);

		assertThat(result.groups())
				.extracting(group -> group.candidates().size())
				.containsExactly(4, 4);
		assertThat(result.groups())
				.extracting(MatchingGroup::topic)
				.containsOnly(LunchTopic.CASUAL_CHAT);
		assertThat(result.remainingCandidates()).isEmpty();
	}

	@Test
	void desperatePairsAreCreatedOnlyInsideTheActivationWindow() {
		List<MatchingCandidate> candidates = List.of(
				candidate(1, LunchTopic.STUDY),
				candidate(2, LunchTopic.NETWORKING)
		);

		MatchingPipelineResult atBoundary = pipeline.match(
				bucket(candidates),
				TIME_SLOT.minusMinutes(5)
		);
		MatchingPipelineResult insideWindow = pipeline.match(
				bucket(candidates),
				TIME_SLOT.minusMinutes(5).plusNanos(1)
		);
		MatchingPipelineResult atTimeSlot = pipeline.match(
				bucket(candidates),
				TIME_SLOT
		);

		assertThat(atBoundary.groups()).isEmpty();
		assertThat(atBoundary.remainingCandidates()).containsExactlyElementsOf(candidates);
		assertThat(insideWindow.groups()).singleElement().satisfies(group -> {
			assertThat(group.topic()).isEqualTo(LunchTopic.CASUAL_CHAT);
			assertThat(group.requestIds()).containsExactlyElementsOf(requestIds(candidates));
		});
		assertThat(insideWindow.remainingCandidates()).isEmpty();
		assertThat(atTimeSlot.groups()).isEmpty();
		assertThat(atTimeSlot.remainingCandidates()).containsExactlyElementsOf(candidates);
	}

	@Test
	void desperateStrategyMatchesPairsAndLeavesOneCandidate() {
		List<MatchingCandidate> candidates = List.of(
				candidate(1, LunchTopic.STUDY),
				candidate(2, LunchTopic.STUDY),
				candidate(3, LunchTopic.STUDY),
				candidate(4, LunchTopic.STUDY),
				candidate(5, LunchTopic.STUDY)
		);
		DesperatePairStrategy strategy = new DesperatePairStrategy();

		MatchingStrategyResult result = strategy.match(
				bucket(candidates.reversed()),
				TIME_SLOT.minusMinutes(1)
		);

		assertThat(result.groups()).hasSize(2);
		assertThat(result.groups())
				.extracting(group -> group.candidates().size())
				.containsOnly(2);
		assertThat(result.remainingCandidates())
				.containsExactly(candidates.getLast());
	}

	@Test
	void resultDoesNotDependOnInputOrStrategyOrder() {
		List<MatchingCandidate> candidates = List.of(
				candidate(1, LunchTopic.STUDY),
				candidate(2, LunchTopic.STUDY),
				candidate(3, LunchTopic.STUDY),
				candidate(4, LunchTopic.STUDY),
				candidate(5, LunchTopic.IT_CAREER),
				candidate(6, LunchTopic.NETWORKING),
				candidate(7, LunchTopic.STARTUPS),
				candidate(8, LunchTopic.CASUAL_CHAT),
				candidate(9, LunchTopic.IT_CAREER)
		);
		List<MatchingCandidate> shuffled = new ArrayList<>(candidates);
		java.util.Collections.rotate(shuffled, 4);
		LunchMatchingPipeline orderedPipeline = new LunchMatchingPipeline(List.of(
				new StrictTopicStrategy(),
				new MixedTopicStrategy(),
				new DesperatePairStrategy()
		));

		MatchingPipelineResult first = pipeline.match(
				bucket(shuffled),
				TIME_SLOT.minusMinutes(4)
		);
		MatchingPipelineResult second = orderedPipeline.match(
				bucket(candidates.reversed()),
				TIME_SLOT.minusMinutes(4)
		);

		assertThat(first).isEqualTo(second);
	}

	@Test
	void pipelineRejectsStrategyThatDropsCandidates() {
		MatchingStrategy invalidStrategy = new MatchingStrategy() {
			@Override
			public int priority() {
				return 1;
			}

			@Override
			public MatchingStrategyResult match(
					MatchingBucket bucket,
					LocalDateTime now
			) {
				return new MatchingStrategyResult(List.of(), List.of());
			}
		};
		LunchMatchingPipeline invalidPipeline = new LunchMatchingPipeline(
				List.of(invalidStrategy)
		);

		assertThatThrownBy(() -> invalidPipeline.match(
				bucket(List.of(candidate(1, LunchTopic.STUDY))),
				TIME_SLOT.minusMinutes(10)
		)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("changed the candidate set");
	}

	@Test
	void bucketRejectsDuplicateRequestsAndProfiles() {
		MatchingCandidate candidate = candidate(1, LunchTopic.STUDY);
		MatchingCandidate sameProfile = new MatchingCandidate(
				new UUID(0, 2),
				candidate.profileId(),
				LunchTopic.NETWORKING,
				CREATED_AT.plusSeconds(2)
		);

		assertThatThrownBy(() -> bucket(List.of(candidate, candidate)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("request IDs");
		assertThatThrownBy(() -> bucket(List.of(candidate, sameProfile)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("profile IDs");
	}

	@Test
	void incompatiblePairDoesNotStopOtherCandidatesFromMatching() {
		List<MatchingCandidate> candidates = List.of(
				candidate(1, LunchTopic.STUDY),
				candidate(2, LunchTopic.STARTUPS),
				candidate(3, LunchTopic.IT_CAREER),
				candidate(4, LunchTopic.NETWORKING),
				candidate(5, LunchTopic.CASUAL_CHAT)
		);
		MatchingProfilePair incompatiblePair = new MatchingProfilePair(
				candidates.get(0).profileId(),
				candidates.get(1).profileId()
		);

		MatchingPipelineResult result = pipeline.match(
				bucket(candidates),
				TIME_SLOT.minusMinutes(10),
				Set.of(incompatiblePair)
		);

		assertThat(result.groups()).singleElement().satisfies(group ->
				assertThat(group.requestIds()).containsExactly(
						candidates.get(0).requestId(),
						candidates.get(2).requestId(),
						candidates.get(3).requestId(),
						candidates.get(4).requestId()
				)
		);
		assertThat(result.remainingCandidates())
				.containsExactly(candidates.get(1));
	}

	private MatchingBucket bucket(List<MatchingCandidate> candidates) {
		return new MatchingBucket(LOCATION_ID, TIME_SLOT, candidates);
	}

	private List<MatchingCandidate> candidates(int count, LunchTopic topic) {
		List<MatchingCandidate> candidates = new ArrayList<>();
		for (int index = 1; index <= count; index++) {
			candidates.add(candidate(index, topic));
		}
		return List.copyOf(candidates);
	}

	private MatchingCandidate candidate(int sequence, LunchTopic topic) {
		return new MatchingCandidate(
				new UUID(0, sequence),
				new UUID(1, sequence),
				topic,
				CREATED_AT.plusSeconds(sequence)
		);
	}

	private List<UUID> requestIds(List<MatchingCandidate> candidates) {
		return candidates.stream().map(MatchingCandidate::requestId).toList();
	}
}
