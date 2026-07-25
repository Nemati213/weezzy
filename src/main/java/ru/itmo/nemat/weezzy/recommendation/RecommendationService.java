package ru.itmo.nemat.weezzy.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.ProfileSkillService;
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
	private final ProfileService profileService;
	private final ProfileSkillService profileSkillService;

	@Transactional(readOnly = true)
	public List<ProfileRecommendationResponse> findRecommendations(UUID profileId) {
		profileService.findById(profileId);

		Set<UUID> sourceSkillIds = profileSkillService.findSkills(profileId).stream()
				.map(Skill::getId)
				.collect(Collectors.toCollection(HashSet::new));

		if (sourceSkillIds.isEmpty()) {
			return List.of();
		}

		return profileService.findAll().stream()
				.filter(candidate -> !candidate.getId().equals(profileId))
				.map(candidate -> recommendationFor(candidate, sourceSkillIds))
				.filter(recommendation -> recommendation.score() > 0)
				.sorted(Comparator
						.comparingInt(ProfileRecommendationResponse::score)
						.reversed()
						.thenComparing(recommendation -> recommendation.profile().displayName()))
				.toList();
	}

	private ProfileRecommendationResponse recommendationFor(Profile candidate, Set<UUID> sourceSkillIds) {
		List<String> matchedSkills = profileSkillService.findSkills(candidate.getId()).stream()
				.filter(skill -> sourceSkillIds.contains(skill.getId()))
				.map(Skill::getName)
				.sorted()
				.toList();

		return new ProfileRecommendationResponse(
				ProfileResponse.from(candidate),
				matchedSkills.size(),
				matchedSkills
		);
	}
}
