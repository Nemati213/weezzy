package ru.itmo.nemat.weezzy.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.goal.Goal;
import ru.itmo.nemat.weezzy.goal.GoalRepository;
import ru.itmo.nemat.weezzy.interest.Interest;
import ru.itmo.nemat.weezzy.interest.InterestRepository;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.dto.ProfileResponse;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoal;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoalRepository;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterest;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterestRepository;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkill;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkillRepository;
import ru.itmo.nemat.weezzy.recommendation.dto.ProfileRecommendationReasonResponse;
import ru.itmo.nemat.weezzy.recommendation.dto.ProfileRecommendationResponse;
import ru.itmo.nemat.weezzy.recommendation.dto.ProfileRecommendationScoreBreakdownResponse;
import ru.itmo.nemat.weezzy.recommendation.dto.ProfileRecommendationSignalCountsResponse;
import ru.itmo.nemat.weezzy.recommendation.dto.RecommendationFilter;
import ru.itmo.nemat.weezzy.recommendation.dto.RecommendationPageResponse;
import ru.itmo.nemat.weezzy.recommendation.impression.ProfileRecommendationImpressionService;
import ru.itmo.nemat.weezzy.skill.Skill;
import ru.itmo.nemat.weezzy.skill.SkillRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {
	private static final int MAX_LIMIT = 100;

	private final ProfileService profileService;
	private final ProfileSkillRepository profileSkillRepository;
	private final ProfileInterestRepository profileInterestRepository;
	private final ProfileGoalRepository profileGoalRepository;
	private final SkillRepository skillRepository;
	private final InterestRepository interestRepository;
	private final GoalRepository goalRepository;
	private final ProfileRecommendationImpressionService impressionService;
	private final RecommendationRankingRepository rankingRepository;
	private final RecommendationCursorCodec cursorCodec;
	private final RecommendationProperties properties;

	@Transactional
	public RecommendationPageResponse findRecommendations(
			UUID profileId,
			int limit,
			String encodedCursor,
			RecommendationFilter filter
	) {
		profileService.findById(profileId);
		int normalizedLimit = normalizeLimit(limit);
		RecommendationCursor cursor = cursorCodec.decode(encodedCursor);

		Set<UUID> sourceSkillIds = profileSkillRepository
				.findAllByProfileId(profileId)
				.stream()
				.map(ProfileSkill::getSkillId)
				.collect(Collectors.toCollection(HashSet::new));
		Set<UUID> sourceInterestIds = profileInterestRepository
				.findAllByProfileId(profileId)
				.stream()
				.map(ProfileInterest::getInterestId)
				.collect(Collectors.toCollection(HashSet::new));
		Set<UUID> sourceGoalIds = profileGoalRepository
				.findAllByProfileId(profileId)
				.stream()
				.map(ProfileGoal::getGoalId)
				.collect(Collectors.toCollection(HashSet::new));

		if (normalizedLimit == 0 || hasNoSignals(
				sourceSkillIds,
				sourceInterestIds,
				sourceGoalIds
		)) {
			return emptyPage();
		}

		LocalDateTime cooldownThreshold = LocalDateTime.now()
				.minus(properties.impressionCooldown());
		List<RankedProfileProjection> rankedProfiles = rankingRepository
				.findRankedProfiles(
						profileId,
						cursor,
						normalizedLimit + 1,
						filter,
						cooldownThreshold
				);
		boolean hasNext = rankedProfiles.size() > normalizedLimit;
		List<RankedProfileProjection> page = rankedProfiles.stream()
				.limit(normalizedLimit)
				.toList();

		if (page.isEmpty()) {
			return emptyPage();
		}

		List<ProfileRecommendationResponse> content = buildContent(
				page,
				sourceSkillIds,
				sourceInterestIds,
				sourceGoalIds
		);
		impressionService.recordImpressions(
				profileId,
				page.stream().map(RankedProfileProjection::profileId).toList()
		);

		String nextCursor = hasNext
				? cursorCodec.encode(toCursor(page.getLast()))
				: null;
		return new RecommendationPageResponse(content, nextCursor);
	}

	private List<ProfileRecommendationResponse> buildContent(
			List<RankedProfileProjection> rankedProfiles,
			Set<UUID> sourceSkillIds,
			Set<UUID> sourceInterestIds,
			Set<UUID> sourceGoalIds
	) {
		Set<UUID> candidateIds = rankedProfiles.stream()
				.map(RankedProfileProjection::profileId)
				.collect(Collectors.toSet());
		Map<UUID, Profile> profilesById = profileService.findAllByIds(candidateIds)
				.stream()
				.collect(Collectors.toMap(Profile::getId, Function.identity()));
		Map<UUID, List<String>> matchedSkills = findMatchedSkillNames(
				candidateIds,
				sourceSkillIds
		);
		Map<UUID, List<String>> matchedInterests = findMatchedInterestNames(
				candidateIds,
				sourceInterestIds
		);
		Map<UUID, List<String>> matchedGoals = findMatchedGoalNames(
				candidateIds,
				sourceGoalIds
		);

		return rankedProfiles.stream()
				.map(ranked -> recommendationFor(
						ranked,
						profilesById.get(ranked.profileId()),
						matchedSkills.getOrDefault(ranked.profileId(), List.of()),
						matchedInterests.getOrDefault(ranked.profileId(), List.of()),
						matchedGoals.getOrDefault(ranked.profileId(), List.of())
				))
				.toList();
	}

	private ProfileRecommendationResponse recommendationFor(
			RankedProfileProjection ranked,
			Profile profile,
			List<String> matchedSkills,
			List<String> matchedInterests,
			List<String> matchedGoals
	) {
		RecommendationProperties.Weights weights = properties.weights();
		ProfileRecommendationReasonResponse reason = new ProfileRecommendationReasonResponse(
				new ProfileRecommendationScoreBreakdownResponse(
						ranked.matchedSkillCount() * weights.skill(),
						ranked.matchedInterestCount() * weights.interest(),
						ranked.matchedGoalCount() * weights.goal()
				),
				new ProfileRecommendationSignalCountsResponse(
						ranked.matchedSkillCount(),
						ranked.matchedInterestCount(),
						ranked.matchedGoalCount()
				)
		);

		return new ProfileRecommendationResponse(
				ProfileResponse.from(profile),
				ranked.score(),
				matchedSkills,
				matchedInterests,
				matchedGoals,
				reason
		);
	}

	private Map<UUID, List<String>> findMatchedSkillNames(
			Set<UUID> candidateIds,
			Set<UUID> sourceSkillIds
	) {
		if (sourceSkillIds.isEmpty()) {
			return Map.of();
		}

		Map<UUID, String> namesById = skillRepository.findAllById(sourceSkillIds)
				.stream()
				.collect(Collectors.toMap(Skill::getId, Skill::getName));
		return groupMatchedNamesByProfile(profileSkillRepository
				.findAllByProfileIdIn(candidateIds)
				.stream()
				.filter(link -> sourceSkillIds.contains(link.getSkillId()))
				.collect(Collectors.groupingBy(
						ProfileSkill::getProfileId,
						Collectors.mapping(
								link -> namesById.get(link.getSkillId()),
								Collectors.toList()
						)
				)));
	}

	private Map<UUID, List<String>> findMatchedInterestNames(
			Set<UUID> candidateIds,
			Set<UUID> sourceInterestIds
	) {
		if (sourceInterestIds.isEmpty()) {
			return Map.of();
		}

		Map<UUID, String> namesById = interestRepository
				.findAllById(sourceInterestIds)
				.stream()
				.collect(Collectors.toMap(Interest::getId, Interest::getName));
		return groupMatchedNamesByProfile(profileInterestRepository
				.findAllByProfileIdIn(candidateIds)
				.stream()
				.filter(link -> sourceInterestIds.contains(link.getInterestId()))
				.collect(Collectors.groupingBy(
						ProfileInterest::getProfileId,
						Collectors.mapping(
								link -> namesById.get(link.getInterestId()),
								Collectors.toList()
						)
				)));
	}

	private Map<UUID, List<String>> findMatchedGoalNames(
			Set<UUID> candidateIds,
			Set<UUID> sourceGoalIds
	) {
		if (sourceGoalIds.isEmpty()) {
			return Map.of();
		}

		Map<UUID, String> namesById = goalRepository.findAllById(sourceGoalIds)
				.stream()
				.collect(Collectors.toMap(Goal::getId, Goal::getName));
		return groupMatchedNamesByProfile(profileGoalRepository
				.findAllByProfileIdIn(candidateIds)
				.stream()
				.filter(link -> sourceGoalIds.contains(link.getGoalId()))
				.collect(Collectors.groupingBy(
						ProfileGoal::getProfileId,
						Collectors.mapping(
								link -> namesById.get(link.getGoalId()),
								Collectors.toList()
						)
				)));
	}

	private Map<UUID, List<String>> groupMatchedNamesByProfile(
			Map<UUID, List<String>> namesByProfile
	) {
		return namesByProfile.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						entry -> entry.getValue().stream().sorted().toList()
				));
	}

	private boolean hasNoSignals(
			Set<UUID> skillIds,
			Set<UUID> interestIds,
			Set<UUID> goalIds
	) {
		return skillIds.isEmpty() && interestIds.isEmpty() && goalIds.isEmpty();
	}

	private RecommendationCursor toCursor(RankedProfileProjection ranked) {
		return new RecommendationCursor(ranked.score(), ranked.profileId());
	}

	private RecommendationPageResponse emptyPage() {
		return new RecommendationPageResponse(List.of(), null);
	}

	private int normalizeLimit(int limit) {
		if (limit <= 0) {
			return 0;
		}
		return Math.min(limit, MAX_LIMIT);
	}
}
