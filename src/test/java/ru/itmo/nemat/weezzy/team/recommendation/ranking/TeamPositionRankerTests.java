package ru.itmo.nemat.weezzy.team.recommendation.ranking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamPositionRankerTests {
	private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 28, 12, 0);
	private static final UUID JAVA = id(1);
	private static final UUID SPRING = id(2);
	private static final UUID POSTGRES = id(3);
	private static final UUID DOCKER = id(4);
	private static final UUID KAFKA = id(5);
	private static final UUID REDIS = id(6);
	private final TeamPositionRanker ranker = new TeamPositionRanker();

	@Test
	void ranksMoreMatchesBeforeFullCoverageOfShorterSkillList() {
		var threeOfFour = position(10, JAVA, SPRING, POSTGRES, DOCKER);
		var twoOfTwo = position(11, JAVA, SPRING);
		var oneOfThree = position(12, JAVA, KAFKA, REDIS);

		var ranked = ranker.rank(Set.of(JAVA, SPRING, POSTGRES),
				List.of(oneOfThree, twoOfTwo, threeOfFour));

		assertThat(ranked).extracting(RankedTeamPosition::position)
				.containsExactly(threeOfFour, twoOfTwo, oneOfThree);
		assertThat(ranked).extracting(RankedTeamPosition::matchedCount)
				.containsExactly(3, 2, 1);
		assertThat(ranked).extracting(RankedTeamPosition::requiredCount)
				.containsExactly(4, 2, 3);
	}

	@Test
	void breaksEqualMatchCountsByCoverageBeforeCreationTime() {
		var twoOfThree = position(10, JAVA, SPRING, DOCKER);
		var twoOfFour = new TeamPositionCandidate(id(11), CREATED_AT.minusDays(1),
				Set.of(JAVA, SPRING, DOCKER, KAFKA));

		assertThat(ranker.rank(Set.of(JAVA, SPRING), List.of(twoOfFour, twoOfThree)))
				.extracting(RankedTeamPosition::position)
				.containsExactly(twoOfThree, twoOfFour);
	}

	@Test
	void comparesCoverageWithoutRoundingPercentages() {
		var oneOfHundred = new TeamPositionCandidate(id(11), CREATED_AT,
				skills(100));
		var oneOfHundredAndOne = new TeamPositionCandidate(id(10), CREATED_AT,
				skills(101));

		assertThat(ranker.rank(Set.of(JAVA), List.of(oneOfHundredAndOne, oneOfHundred)))
				.extracting(RankedTeamPosition::position)
				.containsExactly(oneOfHundred, oneOfHundredAndOne);
	}

	@Test
	void breaksEqualScoresByEarlierCreationBeforeId() {
		var older = new TeamPositionCandidate(id(20), CREATED_AT.minusSeconds(1),
				Set.of(JAVA, DOCKER));
		var newer = position(10, JAVA, KAFKA);

		assertThat(ranker.rank(Set.of(JAVA), List.of(newer, older)))
				.extracting(RankedTeamPosition::position).containsExactly(older, newer);
	}

	@ParameterizedTest
	@CsvSource({
			"7fffffff-ffff-ffff-ffff-ffffffffffff, 80000000-0000-0000-0000-000000000000",
			"00000000-0000-0000-7fff-ffffffffffff, 00000000-0000-0000-8000-000000000000"
	})
	void breaksTiesUsingPostgresUuidOrder(String smallerId, String largerId) {
		var smaller = new TeamPositionCandidate(UUID.fromString(smallerId), CREATED_AT,
				Set.of(JAVA));
		var larger = new TeamPositionCandidate(UUID.fromString(largerId), CREATED_AT,
				Set.of(JAVA));

		assertThat(ranker.rank(Set.of(JAVA), List.of(larger, smaller)))
				.extracting(RankedTeamPosition::position).containsExactly(smaller, larger);
	}

	@Test
	void preservesZeroMatchAndEmptySkillPositionsAfterMatchingPositions() {
		var empty = position(10);
		var zeroMatches = position(11, KAFKA, REDIS);
		var matching = position(12, JAVA, SPRING);

		var ranked = ranker.rank(Set.of(JAVA), List.of(empty, zeroMatches, matching));

		assertThat(ranked).extracting(RankedTeamPosition::position)
				.containsExactly(matching, empty, zeroMatches);
		assertThat(ranked).extracting(RankedTeamPosition::matchedCount)
				.containsExactly(1, 0, 0);
		assertThat(ranked.get(1).requiredCount()).isZero();
		assertThat(ranked.get(1).unmatchedSkillIds()).isEmpty();
		assertThat(ranked.get(2).unmatchedSkillIds()).containsExactly(KAFKA, REDIS);
	}

	@Test
	void treatsAllZeroCoveragePositionsEquallyRegardlessOfSkillCount() {
		var empty = position(12);
		var oneSkill = position(11, KAFKA);
		var twoSkills = position(10, KAFKA, REDIS);

		assertThat(ranker.rank(Set.of(JAVA), List.of(empty, oneSkill, twoSkills)))
				.extracting(RankedTeamPosition::position)
				.containsExactly(twoSkills, oneSkill, empty);
	}

	@Test
	void returnsAllPositionsForProfileWithoutSkills() {
		var empty = position(11);
		var withSkills = position(10, JAVA, SPRING);

		var ranked = ranker.rank(Set.of(), List.of(empty, withSkills));

		assertThat(ranked).extracting(RankedTeamPosition::position)
				.containsExactly(withSkills, empty);
		assertThat(ranked).allSatisfy(result -> {
			assertThat(result.matchedCount()).isZero();
			assertThat(result.matchedSkillIds()).isEmpty();
		});
		assertThat(ranked.getFirst().unmatchedSkillIds()).containsExactly(JAVA, SPRING);
	}

	@Test
	void additionalProfileSkillsDoNotChangeScoresOrExplanations() {
		var positions = List.of(position(10, JAVA, SPRING), position(11, JAVA));
		var original = ranker.rank(Set.of(JAVA), positions);

		assertThat(ranker.rank(Set.of(JAVA, DOCKER, KAFKA, REDIS), positions))
				.isEqualTo(original);
	}

	@Test
	void additionalWeakerPositionsDoNotChangeTheBestPositionOrItsScore() {
		var best = position(10, JAVA, SPRING);
		var positions = new ArrayList<>(List.of(best));
		var originalBest = ranker.rank(Set.of(JAVA, SPRING), positions).getFirst();
		for (long positionId = 11; positionId < 111; positionId++) {
			positions.add(position(positionId, JAVA, DOCKER));
		}

		assertThat(ranker.rank(Set.of(JAVA, SPRING), positions).getFirst())
				.isEqualTo(originalBest);
	}

	@Test
	void explainsEachUniqueRequiredSkillInPostgresUuidOrder() {
		UUID high = UUID.fromString("80000000-0000-0000-0000-000000000000");
		UUID low = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
		UUID highLowerHalf = UUID.fromString("00000000-0000-0000-8000-000000000000");
		UUID lowLowerHalf = UUID.fromString("00000000-0000-0000-7fff-ffffffffffff");
		var candidate = position(10, high, low, highLowerHalf, lowLowerHalf, high);

		var result = ranker.rank(Set.of(high, low, JAVA), List.of(candidate)).getFirst();

		assertThat(result.matchedSkillIds()).containsExactly(low, high);
		assertThat(result.unmatchedSkillIds()).containsExactly(lowLowerHalf, highLowerHalf);
		assertThat(result.matchedCount()).isEqualTo(2);
		assertThat(result.requiredCount()).isEqualTo(4);
	}

	@Test
	void rankingIsIndependentOfInputOrderAndDoesNotReorderTheInput() {
		var first = position(10, JAVA, SPRING);
		var second = position(11, JAVA, SPRING);
		var third = position(12, JAVA);
		var positions = new ArrayList<>(List.of(third, second, first));

		var ranked = ranker.rank(Set.of(JAVA, SPRING), positions);

		assertThat(positions).containsExactly(third, second, first);
		assertThat(ranked).extracting(RankedTeamPosition::position)
				.containsExactly(first, second, third);
		Collections.reverse(positions);
		assertThat(ranker.rank(Set.of(JAVA, SPRING), positions)).isEqualTo(ranked);
	}

	@Test
	void candidateAndResultDoNotExposeMutableSkillCollections() {
		var mutableSkills = new HashSet<>(Set.of(JAVA, DOCKER));
		var candidate = new TeamPositionCandidate(id(10), CREATED_AT, mutableSkills);
		mutableSkills.clear();

		var ranked = ranker.rank(Set.of(JAVA), List.of(candidate));
		var result = ranked.getFirst();

		assertThat(result.matchedSkillIds()).containsExactly(JAVA);
		assertThat(result.unmatchedSkillIds()).containsExactly(DOCKER);
		assertThatThrownBy(() -> candidate.skillIds().clear())
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> result.matchedSkillIds().clear())
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> result.unmatchedSkillIds().clear())
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(ranked::clear).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void emptyCandidatesHaveNoRecommendedPosition() {
		assertThat(ranker.rank(Set.of(JAVA), List.of())).isEmpty();
	}

	private TeamPositionCandidate position(long positionId, UUID... skillIds) {
		return new TeamPositionCandidate(id(positionId), CREATED_AT,
				new HashSet<>(List.of(skillIds)));
	}

	private Set<UUID> skills(long count) {
		return LongStream.rangeClosed(1, count).mapToObj(TeamPositionRankerTests::id)
				.collect(Collectors.toSet());
	}

	private static UUID id(long value) {
		return new UUID(0, value);
	}
}
