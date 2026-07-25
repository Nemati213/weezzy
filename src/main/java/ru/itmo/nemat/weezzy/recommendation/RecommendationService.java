package ru.itmo.nemat.weezzy.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.goal.Goal;
import ru.itmo.nemat.weezzy.interest.Interest;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.goal.ProfileGoalService;
import ru.itmo.nemat.weezzy.profile.interest.ProfileInterestService;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkillService;
import ru.itmo.nemat.weezzy.profile.dto.ProfileResponse;
import ru.itmo.nemat.weezzy.recommendation.dto.ProfileRecommendationResponse;
import ru.itmo.nemat.weezzy.skill.Skill;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {
	private static final int SKILL_WEIGHT = 3;
	private static final int INTEREST_WEIGHT = 2;
	private static final int GOAL_WEIGHT = 5;

	private final ProfileService profileService;
	private final ProfileSkillService profileSkillService;
	private final ProfileInterestService profileInterestService;
	private final ProfileGoalService profileGoalService;

	@Transactional(readOnly = true)
	public List<ProfileRecommendationResponse> findRecommendations(UUID profileId) {
		profileService.findById(profileId);

		Set<UUID> sourceSkillIds = profileSkillService.findSkills(profileId).stream()
				.map(Skill::getId)
				.collect(Collectors.toCollection(HashSet::new));
		Set<UUID> sourceInterestIds = profileInterestService.findInterests(profileId).stream()
				.map(Interest::getId)
				.collect(Collectors.toCollection(HashSet::new));
		Set<UUID> sourceGoalIds = profileGoalService.findGoals(profileId).stream()
				.map(Goal::getId)
				.collect(Collectors.toCollection(HashSet::new));

		if (sourceSkillIds.isEmpty() && sourceInterestIds.isEmpty() && sourceGoalIds.isEmpty()) {
			return List.of();
		}

		return profileService.findAll().stream()
				.filter(candidate -> !candidate.getId().equals(profileId))
				.map(candidate -> recommendationFor(candidate, sourceSkillIds, sourceInterestIds, sourceGoalIds))
				.filter(recommendation -> recommendation.score() > 0)
				.sorted(Comparator
						.comparingInt(ProfileRecommendationResponse::score)
						.reversed()
						.thenComparing(recommendation -> recommendation.profile().displayName()))
				.toList();
	}

	private ProfileRecommendationResponse recommendationFor(
			Profile candidate,
			Set<UUID> sourceSkillIds,
			Set<UUID> sourceInterestIds,
			Set<UUID> sourceGoalIds
	) {
		List<String> matchedSkills = profileSkillService.findSkills(candidate.getId()).stream()
				.filter(skill -> sourceSkillIds.contains(skill.getId()))
				.map(Skill::getName)
				.sorted()
				.toList();
		List<String> matchedInterests = profileInterestService.findInterests(candidate.getId()).stream()
				.filter(interest -> sourceInterestIds.contains(interest.getId()))
				.map(Interest::getName)
				.sorted()
				.toList();
		List<String> matchedGoals = profileGoalService.findGoals(candidate.getId()).stream()
				.filter(goal -> sourceGoalIds.contains(goal.getId()))
				.map(Goal::getName)
				.sorted()
				.toList();
		int score = matchedSkills.size() * SKILL_WEIGHT
				+ matchedInterests.size() * INTEREST_WEIGHT
				+ matchedGoals.size() * GOAL_WEIGHT;

		return new ProfileRecommendationResponse(
				ProfileResponse.from(candidate),
				score,
				matchedSkills,
				matchedInterests,
				matchedGoals
		);
	}
}
