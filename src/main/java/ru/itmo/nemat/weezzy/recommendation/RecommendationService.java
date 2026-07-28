package ru.itmo.nemat.weezzy.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.vote.ProfileVoteService;
import ru.itmo.nemat.weezzy.goal.Goal;
import ru.itmo.nemat.weezzy.goal.GoalRepository;
import ru.itmo.nemat.weezzy.interest.Interest;
import ru.itmo.nemat.weezzy.interest.InterestRepository;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoal;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoalRepository;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoalService;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterest;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterestRepository;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterestService;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkill;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkillRepository;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkillService;
import ru.itmo.nemat.weezzy.profile.dto.ProfileResponse;
import ru.itmo.nemat.weezzy.recommendation.dto.ProfileRecommendationReasonResponse;
import ru.itmo.nemat.weezzy.recommendation.dto.ProfileRecommendationResponse;
import ru.itmo.nemat.weezzy.recommendation.dto.ProfileRecommendationScoreBreakdownResponse;
import ru.itmo.nemat.weezzy.recommendation.dto.ProfileRecommendationSignalCountsResponse;
import ru.itmo.nemat.weezzy.recommendation.dto.RecommendationPageResponse;
import ru.itmo.nemat.weezzy.skill.Skill;
import ru.itmo.nemat.weezzy.skill.SkillRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {
	private static final int SKILL_WEIGHT = 3;
	private static final int INTEREST_WEIGHT = 2;
	private static final int GOAL_WEIGHT = 5;
	private static final int MAX_LIMIT = 100;
	private static final Base64.Encoder CURSOR_ENCODER = Base64.getUrlEncoder()
			.withoutPadding();
	private static final Base64.Decoder CURSOR_DECODER = Base64.getUrlDecoder();

	private final ProfileService profileService;
	private final ProfileSkillService profileSkillService;
	private final ProfileInterestService profileInterestService;
	private final ProfileGoalService profileGoalService;
	private final ProfileVoteService profileVoteService;
	private final ProfileSkillRepository profileSkillRepository;
	private final ProfileInterestRepository profileInterestRepository;
	private final ProfileGoalRepository profileGoalRepository;
	private final SkillRepository skillRepository;
	private final InterestRepository interestRepository;
	private final GoalRepository goalRepository;

	@Transactional(readOnly = true)
	public RecommendationPageResponse findRecommendations(
			UUID profileId,
			int limit,
			String cursor
	) {
		profileService.findById(profileId);
		int normalizedLimit = normalizeLimit(limit);
		RecommendationCursor recommendationCursor = decodeCursor(cursor);

		Set<UUID> sourceSkillIds = profileSkillService.findSkills(profileId).stream()
				.map(Skill::getId)
				.collect(Collectors.toCollection(HashSet::new));
		Set<UUID> sourceInterestIds = profileInterestService.findInterests(profileId).stream()
				.map(Interest::getId)
				.collect(Collectors.toCollection(HashSet::new));
		Set<UUID> sourceGoalIds = profileGoalService.findGoals(profileId).stream()
				.map(Goal::getId)
				.collect(Collectors.toCollection(HashSet::new));

		Set<UUID> sourceVotes = profileVoteService.findVotedTargetProfileIds(profileId);

		if (normalizedLimit == 0
				|| sourceSkillIds.isEmpty() && sourceInterestIds.isEmpty() && sourceGoalIds.isEmpty()) {
			return new RecommendationPageResponse(List.of(), null);
		}

		List<Profile> candidates = profileService.findAll().stream()
				.filter(candidate -> !candidate.getId().equals(profileId))
				.filter(candidate -> candidate.getStatus().equals(ProfileStatus.ACTIVE))
				.filter(candidate -> !sourceVotes.contains(candidate.getId()))
				.toList();

		if (candidates.isEmpty()) {
			return new RecommendationPageResponse(List.of(), null);
		}

		Set<UUID> candidateIds = candidates.stream()
				.map(Profile::getId)
				.collect(Collectors.toSet());
		Map<UUID, List<String>> matchedSkillsByProfile = findMatchedSkillNames(candidateIds, sourceSkillIds);
		Map<UUID, List<String>> matchedInterestsByProfile = findMatchedInterestNames(candidateIds, sourceInterestIds);
		Map<UUID, List<String>> matchedGoalsByProfile = findMatchedGoalNames(candidateIds, sourceGoalIds);

		List<ProfileRecommendationResponse> sortedRecommendations = candidates.stream()
				.map(candidate -> recommendationFor(
						candidate,
						matchedSkillsByProfile.getOrDefault(candidate.getId(), List.of()),
						matchedInterestsByProfile.getOrDefault(candidate.getId(), List.of()),
						matchedGoalsByProfile.getOrDefault(candidate.getId(), List.of())
				))
				.filter(recommendation -> recommendation.score() > 0)
				.sorted(Comparator
						.comparingInt(ProfileRecommendationResponse::score)
						.reversed()
						.thenComparing(recommendation -> recommendation.profile().id()))
				.toList();

		List<ProfileRecommendationResponse> page = sortedRecommendations.stream()
				.filter(recommendation -> isAfterCursor(recommendation, recommendationCursor))
				.limit(normalizedLimit + 1L)
				.toList();

		if (page.size() <= normalizedLimit) {
			return new RecommendationPageResponse(page, null);
		}

		List<ProfileRecommendationResponse> content = page.stream()
				.limit(normalizedLimit)
				.toList();
		ProfileRecommendationResponse lastRecommendation = content.get(content.size() - 1);

		return new RecommendationPageResponse(
				content,
				encodeCursor(lastRecommendation)
		);
	}

	private ProfileRecommendationResponse recommendationFor(
			Profile candidate,
			List<String> matchedSkills,
			List<String> matchedInterests,
			List<String> matchedGoals
	) {
		int score = matchedSkills.size() * SKILL_WEIGHT
				+ matchedInterests.size() * INTEREST_WEIGHT
				+ matchedGoals.size() * GOAL_WEIGHT;
		ProfileRecommendationReasonResponse reason = new ProfileRecommendationReasonResponse(
				new ProfileRecommendationScoreBreakdownResponse(
						matchedSkills.size() * SKILL_WEIGHT,
						matchedInterests.size() * INTEREST_WEIGHT,
						matchedGoals.size() * GOAL_WEIGHT
				),
				new ProfileRecommendationSignalCountsResponse(
						matchedSkills.size(),
						matchedInterests.size(),
						matchedGoals.size()
				)
		);

		return new ProfileRecommendationResponse(
				ProfileResponse.from(candidate),
				score,
				matchedSkills,
				matchedInterests,
				matchedGoals,
				reason
		);
	}

	private boolean isAfterCursor(
			ProfileRecommendationResponse recommendation,
			RecommendationCursor cursor
	) {
		if (cursor == null) {
			return true;
		}
		if (recommendation.score() != cursor.score()) {
			return recommendation.score() < cursor.score();
		}
		return recommendation.profile().id().compareTo(cursor.profileId()) > 0;
	}

	private String encodeCursor(ProfileRecommendationResponse recommendation) {
		String rawCursor = recommendation.score() + ":" + recommendation.profile().id();
		return CURSOR_ENCODER.encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
	}

	private RecommendationCursor decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}

		try {
			String rawCursor = new String(CURSOR_DECODER.decode(cursor), StandardCharsets.UTF_8);
			String[] parts = rawCursor.split(":", 2);
			if (parts.length != 2) {
				throw new IllegalArgumentException();
			}
			return new RecommendationCursor(Integer.parseInt(parts[0]), UUID.fromString(parts[1]));
		} catch (IllegalArgumentException exception) {
			throw new InvalidRecommendationCursorException();
		}
	}

	private Map<UUID, List<String>> findMatchedSkillNames(Set<UUID> candidateIds, Set<UUID> sourceSkillIds) {
		if (sourceSkillIds.isEmpty()) {
			return Map.of();
		}

		List<ProfileSkill> matchedLinks = profileSkillRepository.findAllByProfileIdIn(candidateIds).stream()
				.filter(profileSkill -> sourceSkillIds.contains(profileSkill.getSkillId()))
				.toList();
		Map<UUID, String> skillNamesById = skillRepository.findAllById(sourceSkillIds).stream()
				.collect(Collectors.toMap(Skill::getId, Skill::getName));

		return groupMatchedNamesByProfile(
				matchedLinks.stream()
						.collect(Collectors.groupingBy(
								ProfileSkill::getProfileId,
								Collectors.mapping(profileSkill -> skillNamesById.get(profileSkill.getSkillId()), Collectors.toList())
						))
		);
	}

	private Map<UUID, List<String>> findMatchedInterestNames(Set<UUID> candidateIds, Set<UUID> sourceInterestIds) {
		if (sourceInterestIds.isEmpty()) {
			return Map.of();
		}

		List<ProfileInterest> matchedLinks = profileInterestRepository.findAllByProfileIdIn(candidateIds).stream()
				.filter(profileInterest -> sourceInterestIds.contains(profileInterest.getInterestId()))
				.toList();
		Map<UUID, String> interestNamesById = interestRepository.findAllById(sourceInterestIds).stream()
				.collect(Collectors.toMap(Interest::getId, Interest::getName));

		return groupMatchedNamesByProfile(
				matchedLinks.stream()
						.collect(Collectors.groupingBy(
								ProfileInterest::getProfileId,
								Collectors.mapping(profileInterest -> interestNamesById.get(profileInterest.getInterestId()), Collectors.toList())
						))
		);
	}

	private Map<UUID, List<String>> findMatchedGoalNames(Set<UUID> candidateIds, Set<UUID> sourceGoalIds) {
		if (sourceGoalIds.isEmpty()) {
			return Map.of();
		}

		List<ProfileGoal> matchedLinks = profileGoalRepository.findAllByProfileIdIn(candidateIds).stream()
				.filter(profileGoal -> sourceGoalIds.contains(profileGoal.getGoalId()))
				.toList();
		Map<UUID, String> goalNamesById = goalRepository.findAllById(sourceGoalIds).stream()
				.collect(Collectors.toMap(Goal::getId, Goal::getName));

		return groupMatchedNamesByProfile(
				matchedLinks.stream()
						.collect(Collectors.groupingBy(
								ProfileGoal::getProfileId,
								Collectors.mapping(profileGoal -> goalNamesById.get(profileGoal.getGoalId()), Collectors.toList())
						))
		);
	}

	private Map<UUID, List<String>> groupMatchedNamesByProfile(Map<UUID, List<String>> matchedNamesByProfile) {
		return matchedNamesByProfile.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						entry -> entry.getValue().stream()
								.sorted()
								.toList()
				));
	}

	private int normalizeLimit(int limit) {
		if (limit <= 0) {
			return 0;
		}

		return Math.min(limit, MAX_LIMIT);
	}

	private record RecommendationCursor(int score, UUID profileId) {

	}
}
