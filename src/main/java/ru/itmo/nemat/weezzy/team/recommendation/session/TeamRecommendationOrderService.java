package ru.itmo.nemat.weezzy.team.recommendation.session;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkillService;
import ru.itmo.nemat.weezzy.team.application.TeamApplicationProperties;
import ru.itmo.nemat.weezzy.team.position.skill.TeamPositionSkillRepository;
import ru.itmo.nemat.weezzy.team.recommendation.TeamRecommendationProperties;
import ru.itmo.nemat.weezzy.team.recommendation.impression.TeamRecommendationImpressionRepository;
import ru.itmo.nemat.weezzy.team.recommendation.candidate.TeamRecommendationCandidate;
import ru.itmo.nemat.weezzy.team.recommendation.candidate.TeamRecommendationCandidateRepository;
import ru.itmo.nemat.weezzy.team.recommendation.ranking.RankedTeamPosition;
import ru.itmo.nemat.weezzy.team.recommendation.ranking.TeamPositionCandidate;
import ru.itmo.nemat.weezzy.team.recommendation.ranking.TeamPositionRanker;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamRecommendationOrderService {
	private static final int BATCH_SIZE = 500;
	private static final Comparator<TeamScore> SCORE_ORDER = Comparator
			.comparingInt(TeamScore::matched).reversed()
			.thenComparing((left, right) -> Long.compare(
					(long) right.matched() * Math.max(1, left.required()),
					(long) left.matched() * Math.max(1, right.required())))
			.thenComparing(TeamScore::freshness, Comparator.reverseOrder())
			.thenComparing(score -> score.teamId().toString());

	private final TeamRecommendationCandidateRepository candidates;
	private final TeamPositionSkillRepository skills;
	private final ProfileService profiles;
	private final ProfileSkillService profileSkills;
	private final TeamApplicationProperties applicationProperties;
	private final TeamPositionRanker ranker;
	private final TeamRecommendationImpressionRepository impressions;
	private final TeamRecommendationProperties properties;

	@Transactional(readOnly = true, propagation = Propagation.MANDATORY)
	public List<UUID> build(UUID userId, LocalDateTime now) {
		UUID profileId = profiles.findOptionalIdByUserId(userId).orElse(null);
		Set<UUID> viewerSkills = profileId == null ? Set.of() : profileSkills.findSkillIds(profileId);
		Map<UUID, TeamScore> teams = new HashMap<>();
		UUID after = null;
		while (true) {
			var batch = candidates.findEligible(profileId, now,
					now.minus(applicationProperties.reapplicationCooldown()),
					after, PageRequest.of(0, BATCH_SIZE));
			if (batch.isEmpty()) {
				break;
			}
			addBatch(teams, batch, viewerSkills);
			after = batch.getLast().requestPositionId();
			if (batch.size() < BATCH_SIZE) {
				break;
			}
		}
		var recent = recentImpressions(userId, teams.keySet(), now);
		Comparator<TeamScore> order = Comparator
				.comparing((TeamScore score) -> recent.get(score.teamId()),
						Comparator.nullsFirst(Comparator.naturalOrder()))
				.thenComparing(SCORE_ORDER);
		return teams.values().stream().sorted(order).map(TeamScore::teamId).toList();
	}

	private Map<UUID, LocalDateTime> recentImpressions(
			UUID userId, Set<UUID> teamIds, LocalDateTime now
	) {
		Map<UUID, LocalDateTime> recent = new HashMap<>();
		var ids = List.copyOf(teamIds);
		LocalDateTime cutoff = now.minus(properties.impressionCooldown());
		for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
			var batch = ids.subList(start, Math.min(start + BATCH_SIZE, ids.size()));
			for (var impression : impressions.findByOwnerUserIdAndTeamIdIn(userId, batch)) {
				if (impression.getLastShownAt().isAfter(cutoff)) {
					recent.put(impression.getTeamId(), impression.getLastShownAt());
				}
			}
		}
		return recent;
	}

	private void addBatch(Map<UUID, TeamScore> teams, List<TeamRecommendationCandidate> batch,
			Set<UUID> viewerSkills) {
		Map<UUID, TeamRecommendationCandidate> positions = new HashMap<>();
		batch.forEach(candidate -> positions.putIfAbsent(candidate.teamPositionId(), candidate));
		Map<UUID, Set<UUID>> positionSkills = new HashMap<>();
		for (var skill : skills.findSkillIdsByPositionIds(positions.keySet())) {
			positionSkills.computeIfAbsent(skill.getPositionId(), ignored -> new HashSet<>())
					.add(skill.getSkillId());
		}
		var ranked = ranker.rank(viewerSkills, positions.values().stream()
				.map(candidate -> new TeamPositionCandidate(candidate.teamPositionId(),
						candidate.positionCreatedAt(),
						positionSkills.getOrDefault(candidate.teamPositionId(), Set.of()))).toList());
		Map<UUID, RankedTeamPosition> scores = new HashMap<>();
		ranked.forEach(position -> scores.put(position.position().positionId(), position));
		for (var candidate : batch) {
			var position = scores.get(candidate.teamPositionId());
			TeamScore score = new TeamScore(candidate.teamId(), position.matchedCount(),
					position.requiredCount(), candidate.requestCreatedAt());
			teams.merge(candidate.teamId(), score, (left, right) -> {
				TeamScore best = SCORE_ORDER.compare(left, right) <= 0 ? left : right;
				LocalDateTime freshness = left.freshness().isAfter(right.freshness())
						? left.freshness() : right.freshness();
				return new TeamScore(left.teamId(), best.matched(), best.required(), freshness);
			});
		}
	}

	private record TeamScore(UUID teamId, int matched, int required, LocalDateTime freshness) {
	}
}
