package ru.itmo.nemat.weezzy.team.recommendation.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkillService;
import ru.itmo.nemat.weezzy.team.application.TeamApplicationProperties;
import ru.itmo.nemat.weezzy.team.position.skill.TeamPositionSkillId;
import ru.itmo.nemat.weezzy.team.position.skill.TeamPositionSkillRepository;
import ru.itmo.nemat.weezzy.team.recommendation.TeamRecommendationProperties;
import ru.itmo.nemat.weezzy.team.recommendation.impression.TeamRecommendationImpression;
import ru.itmo.nemat.weezzy.team.recommendation.impression.TeamRecommendationImpressionRepository;
import ru.itmo.nemat.weezzy.team.recommendation.candidate.TeamRecommendationCandidate;
import ru.itmo.nemat.weezzy.team.recommendation.candidate.TeamRecommendationCandidateRepository;
import ru.itmo.nemat.weezzy.team.recommendation.ranking.TeamPositionRanker;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamRecommendationOrderTests {
	private final TeamRecommendationCandidateRepository candidates = mock(TeamRecommendationCandidateRepository.class);
	private final TeamPositionSkillRepository skills = mock(TeamPositionSkillRepository.class);
	private final ProfileService profiles = mock(ProfileService.class);
	private final ProfileSkillService profileSkills = mock(ProfileSkillService.class);
	private final TeamRecommendationImpressionRepository impressions = mock(TeamRecommendationImpressionRepository.class);
	private final TeamRecommendationProperties properties = mock(TeamRecommendationProperties.class);
	private final TeamRecommendationOrderService service = new TeamRecommendationOrderService(
			candidates, skills, profiles, profileSkills,
			new TeamApplicationProperties(Duration.ofHours(24)), new TeamPositionRanker(),
			impressions, properties);
	private final UUID user = UUID.randomUUID();
	private final UUID profile = UUID.randomUUID();
	private final LocalDateTime now = LocalDateTime.of(2026, 10, 30, 12, 0);

	@BeforeEach
	void setUp() {
		when(profiles.findOptionalIdByUserId(user)).thenReturn(Optional.of(profile));
		when(properties.impressionCooldown()).thenReturn(Duration.ofDays(3));
	}

	@Test
	void ranksByBestPositionCoverageThenFreshnessAndKeepsZeroMatches() {
		UUID java = UUID.randomUUID();
		UUID spring = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		var best = candidate(UUID.randomUUID(), now.minusDays(3));
		var partial = candidate(UUID.randomUUID(), now.minusDays(2));
		var exact = candidate(UUID.randomUUID(), now.minusDays(4));
		var zero = candidate(UUID.randomUUID(), now);
		when(profileSkills.findSkillIds(profile)).thenReturn(Set.of(java, spring));
		firstBatch(List.of(zero, partial, exact, best));
		when(skills.findSkillIdsByPositionIds(any())).thenReturn(List.of(
				new TeamPositionSkillId(best.teamPositionId(), java),
				new TeamPositionSkillId(best.teamPositionId(), spring),
				new TeamPositionSkillId(best.teamPositionId(), other),
				new TeamPositionSkillId(partial.teamPositionId(), java),
				new TeamPositionSkillId(partial.teamPositionId(), other),
				new TeamPositionSkillId(exact.teamPositionId(), java)));
		assertThat(service.build(user, now)).containsExactly(
				best.teamId(), exact.teamId(), partial.teamId(), zero.teamId());
		verify(profileSkills).findSkillIds(profile);
		verify(skills).findSkillIdsByPositionIds(any());
	}

	@Test
	void usesNewestEligiblePublicationEvenWhenItsPositionIsNotBest() {
		UUID skill = UUID.randomUUID();
		var old = candidate(UUID.randomUUID(), now.minusDays(2));
		var freshOption = candidate(old.teamId(), now);
		var competing = candidate(UUID.randomUUID(), now.minusDays(1));
		when(profileSkills.findSkillIds(profile)).thenReturn(Set.of(skill));
		firstBatch(List.of(old, competing, freshOption));
		when(skills.findSkillIdsByPositionIds(any())).thenReturn(List.of(
				new TeamPositionSkillId(old.teamPositionId(), skill),
				new TeamPositionSkillId(competing.teamPositionId(), skill)));
		assertThat(service.build(user, now)).containsExactly(old.teamId(), competing.teamId());
	}

	@Test
	void missingProfileKeepsAllCandidatesWithPostgresUuidTieBreak() {
		when(profiles.findOptionalIdByUserId(user)).thenReturn(Optional.empty());
		UUID low = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
		UUID high = UUID.fromString("80000000-0000-0000-0000-000000000000");
		firstBatch(List.of(candidate(high, now), candidate(low, now)));
		assertThat(service.build(user, now)).containsExactly(low, high);
		verifyNoInteractions(profileSkills);
	}

	@Test
	void mergesAcrossBatchesWithoutBoostFromRepeatedPublications() {
		var repeated = candidate(UUID.randomUUID(), now.minusDays(2));
		var newer = candidate(UUID.randomUUID(), now);
		List<TeamRecommendationCandidate> batch = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			batch.add(new TeamRecommendationCandidate(repeated.teamId(), UUID.randomUUID(),
					repeated.requestCreatedAt(), now.plusDays(1), UUID.randomUUID(),
					repeated.teamPositionId(), repeated.positionCreatedAt()));
		}
		firstBatch(batch);
		when(candidates.findEligible(eq(profile), eq(now), eq(now.minusHours(24)),
				eq(batch.getLast().requestPositionId()), any())).thenReturn(List.of(repeated, newer));
		assertThat(service.build(user, now)).containsExactly(newer.teamId(), repeated.teamId());
		verify(candidates, times(2)).findEligible(eq(profile), eq(now), eq(now.minusHours(24)), any(), any());
		verify(skills, times(2)).findSkillIdsByPositionIds(any());
		verify(profileSkills).findSkillIds(profile);
	}

	@Test
	void emptyCatalogSkipsSkillQueries() {
		assertThat(service.build(user, now)).isEmpty();
		verifyNoInteractions(skills, impressions);
	}

	@Test
	void unseenZeroMatchComesBeforeRecentlyShownMatchingTeam() {
		UUID skill = UUID.randomUUID();
		var matching = candidate(UUID.randomUUID(), now);
		var zero = candidate(UUID.randomUUID(), now.minusDays(2));
		when(profileSkills.findSkillIds(profile)).thenReturn(Set.of(skill));
		firstBatch(List.of(matching, zero));
		when(skills.findSkillIdsByPositionIds(any())).thenReturn(List.of(
				new TeamPositionSkillId(matching.teamPositionId(), skill)));
		when(impressions.findByOwnerUserIdAndTeamIdIn(eq(user), any()))
				.thenReturn(List.of(impression(matching.teamId(), now.minusDays(1))));
		assertThat(service.build(user, now)).containsExactly(zero.teamId(), matching.teamId());
	}

	@Test
	void cooldownFallbackUsesOldestImpressionBeforeSkillsAndBreaksTiesNormally() {
		UUID skill = UUID.randomUUID();
		var older = candidate(UUID.randomUUID(), now.minusDays(2));
		var matching = candidate(UUID.randomUUID(), now);
		var zero = candidate(UUID.randomUUID(), now.plusHours(1));
		when(profileSkills.findSkillIds(profile)).thenReturn(Set.of(skill));
		firstBatch(List.of(zero, matching, older));
		when(skills.findSkillIdsByPositionIds(any())).thenReturn(List.of(
				new TeamPositionSkillId(matching.teamPositionId(), skill)));
		when(impressions.findByOwnerUserIdAndTeamIdIn(eq(user), any())).thenReturn(List.of(
				impression(older.teamId(), now.minusDays(2)),
				impression(matching.teamId(), now.minusHours(1)),
				impression(zero.teamId(), now.minusHours(1))));
		assertThat(service.build(user, now)).containsExactly(older.teamId(), matching.teamId(), zero.teamId());
	}

	@Test
	void exactConfiguredBoundaryLeavesCooldownAndOldHistoryDoesNotAffectBaseOrder() {
		when(properties.impressionCooldown()).thenReturn(Duration.ofHours(2));
		var exact = candidate(UUID.randomUUID(), now);
		var older = candidate(UUID.randomUUID(), now.minusDays(1));
		var recent = candidate(UUID.randomUUID(), now.plusDays(1));
		firstBatch(List.of(recent, older, exact));
		when(impressions.findByOwnerUserIdAndTeamIdIn(eq(user), any())).thenReturn(List.of(
				impression(exact.teamId(), now.minusHours(2)),
				impression(older.teamId(), now.minusHours(3)),
				impression(recent.teamId(), now.minusHours(2).plusNanos(1000))));
		assertThat(service.build(user, now)).containsExactly(exact.teamId(), older.teamId(), recent.teamId());
	}

	@Test
	void historyReadsAreBoundedByFiveHundredUniqueTeams() {
		var batch = java.util.stream.IntStream.range(0, 500)
				.mapToObj(i -> candidate(UUID.randomUUID(), now)).toList();
		firstBatch(batch);
		when(candidates.findEligible(eq(profile), eq(now), eq(now.minusHours(24)),
				eq(batch.getLast().requestPositionId()), any()))
				.thenReturn(List.of(candidate(UUID.randomUUID(), now), batch.getFirst()));
		List<Integer> sizes = new ArrayList<>();
		when(impressions.findByOwnerUserIdAndTeamIdIn(eq(user), any())).thenAnswer(invocation -> {
			java.util.Collection<UUID> ids = invocation.getArgument(1);
			sizes.add(ids.size());
			return List.of();
		});
		assertThat(service.build(user, now)).hasSize(501).doesNotHaveDuplicates();
		assertThat(sizes).containsExactly(500, 1);
	}

	private TeamRecommendationImpression impression(UUID team, LocalDateTime shownAt) {
		var impression = new TeamRecommendationImpression();
		impression.setTeamId(team);
		impression.setOwnerUserId(user);
		impression.setLastShownAt(shownAt);
		return impression;
	}

	private void firstBatch(List<TeamRecommendationCandidate> batch) {
		when(candidates.findEligible(eq(profile), eq(now), eq(now.minusHours(24)),
				isNull(), eq(PageRequest.of(0, 500)))).thenReturn(batch);
		when(candidates.findEligible(isNull(), eq(now), eq(now.minusHours(24)),
				isNull(), eq(PageRequest.of(0, 500)))).thenReturn(batch);
	}

	private TeamRecommendationCandidate candidate(UUID team, LocalDateTime created) {
		return new TeamRecommendationCandidate(team, UUID.randomUUID(), created, now.plusDays(1),
				UUID.randomUUID(), UUID.randomUUID(), now.minusDays(10));
	}
}
