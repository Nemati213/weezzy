package ru.itmo.nemat.weezzy.lunch.matching;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class LunchMatchingPipeline {
	private final List<MatchingStrategy> strategies;

	public LunchMatchingPipeline(List<MatchingStrategy> strategies) {
		this.strategies = strategies.stream()
				.sorted(Comparator
						.comparingInt(MatchingStrategy::priority)
						.thenComparing(strategy -> strategy.getClass().getName()))
				.toList();
	}

	public MatchingPipelineResult match(
			MatchingBucket bucket,
			LocalDateTime now
	) {
		return match(bucket, now, Set.of());
	}

	public MatchingPipelineResult match(
			MatchingBucket bucket,
			LocalDateTime now,
			Set<MatchingProfilePair> incompatiblePairs
	) {
		Objects.requireNonNull(bucket, "bucket must not be null");
		Objects.requireNonNull(now, "now must not be null");
		Set<MatchingProfilePair> normalizedPairs = Set.copyOf(
				Objects.requireNonNull(
						incompatiblePairs,
						"incompatiblePairs must not be null"
				)
		);
		List<MatchingCandidate> excluded = new ArrayList<>();
		MatchingBucket currentBucket = bucket;
		MatchingPipelineResult result;

		do {
			result = matchUnconstrained(currentBucket, now);
			MatchingCandidate candidateToExclude = findCandidateToExclude(
					result.groups(),
					normalizedPairs
			);
			if (candidateToExclude == null) {
				List<MatchingCandidate> remaining = new ArrayList<>(
						result.remainingCandidates()
				);
				remaining.addAll(excluded);
				return new MatchingPipelineResult(
						result.groups(),
						remaining.stream()
								.sorted(MatchingSupport.CANDIDATE_ORDER)
								.toList()
				);
			}
			excluded.add(candidateToExclude);
			currentBucket = currentBucket.withCandidates(
					currentBucket.candidates().stream()
							.filter(candidate -> !candidate.equals(candidateToExclude))
							.toList()
			);
		} while (true);
	}

	private MatchingPipelineResult matchUnconstrained(
			MatchingBucket bucket,
			LocalDateTime now
	) {
		MatchingBucket currentBucket = bucket.withCandidates(
				bucket.candidates().stream()
						.sorted(MatchingSupport.CANDIDATE_ORDER)
						.toList()
		);
		List<MatchingGroup> groups = new ArrayList<>();

		for (MatchingStrategy strategy : strategies) {
			MatchingStrategyResult result = Objects.requireNonNull(
					strategy.match(currentBucket, now),
					"matching strategy result must not be null"
			);
			validateResult(currentBucket.candidates(), result, strategy);
			groups.addAll(result.groups());
			currentBucket = currentBucket.withCandidates(
					result.remainingCandidates()
			);
		}

		return new MatchingPipelineResult(
				groups,
				currentBucket.candidates()
		);
	}

	private MatchingCandidate findCandidateToExclude(
			List<MatchingGroup> groups,
			Set<MatchingProfilePair> incompatiblePairs
	) {
		for (MatchingGroup group : groups) {
			for (int firstIndex = 0;
					firstIndex < group.candidates().size();
					firstIndex++) {
				for (int secondIndex = firstIndex + 1;
						secondIndex < group.candidates().size();
						secondIndex++) {
					MatchingCandidate first = group.candidates().get(firstIndex);
					MatchingCandidate second = group.candidates().get(secondIndex);
					if (incompatiblePairs.contains(new MatchingProfilePair(
							first.profileId(),
							second.profileId()
					))) {
						return MatchingSupport.CANDIDATE_ORDER.compare(first, second) > 0
								? first
								: second;
					}
				}
			}
		}
		return null;
	}

	private void validateResult(
			List<MatchingCandidate> input,
			MatchingStrategyResult result,
			MatchingStrategy strategy
	) {
		Map<UUID, MatchingCandidate> inputByRequestId = new HashMap<>();
		input.forEach(candidate -> inputByRequestId.put(
				candidate.requestId(),
				candidate
		));
		List<MatchingCandidate> output = new ArrayList<>();
		result.groups().forEach(group -> output.addAll(group.candidates()));
		output.addAll(result.remainingCandidates());
		Set<UUID> outputRequestIds = new HashSet<>();

		boolean valid = output.size() == input.size()
				&& output.stream().allMatch(candidate ->
						candidate.equals(inputByRequestId.get(candidate.requestId()))
								&& outputRequestIds.add(candidate.requestId())
				);
		if (!valid) {
			throw new IllegalStateException(
					"matching strategy changed the candidate set: "
							+ strategy.getClass().getSimpleName()
			);
		}
	}
}
