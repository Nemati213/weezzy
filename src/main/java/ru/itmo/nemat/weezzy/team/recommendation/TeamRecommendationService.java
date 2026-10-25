package ru.itmo.nemat.weezzy.team.recommendation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.photo.ProfilePhotoService;
import ru.itmo.nemat.weezzy.profile.skill.ProfileSkillService;
import ru.itmo.nemat.weezzy.skill.dto.SkillResponse;
import ru.itmo.nemat.weezzy.team.Team;
import ru.itmo.nemat.weezzy.team.application.TeamApplicationProperties;
import ru.itmo.nemat.weezzy.team.dto.TeamMemberResponse;
import ru.itmo.nemat.weezzy.team.member.TeamMemberRepository;
import ru.itmo.nemat.weezzy.team.member.TeamMemberVisibilityService;
import ru.itmo.nemat.weezzy.team.position.TeamPosition;
import ru.itmo.nemat.weezzy.team.position.TeamPositionService;
import ru.itmo.nemat.weezzy.team.position.dto.TeamPositionResponse;
import ru.itmo.nemat.weezzy.team.recommendation.candidate.TeamRecommendationCandidateRepository;
import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationPositionResponse;
import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationRequestResponse;
import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationTeamResponse;
import ru.itmo.nemat.weezzy.team.recommendation.ranking.TeamPositionCandidate;
import ru.itmo.nemat.weezzy.team.recommendation.ranking.TeamPositionRanker;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPosition;
import ru.itmo.nemat.weezzy.team.request.position.dto.TeamRequestPositionResponse;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Validated
@RequiredArgsConstructor
public class TeamRecommendationService {
	private static final Comparator<TeamRequestPosition> PUBLICATION_ORDER = Comparator
			.comparing((TeamRequestPosition link) -> link.getRequest().getRecruitmentDeadline())
			.thenComparing(link -> link.getRequest().getCreatedAt(), Comparator.reverseOrder())
			.thenComparing(link -> link.getRequest().getId().toString());

	private final AccountAccessService accountAccessService;
	private final ProfileService profileService;
	private final ProfileSkillService profileSkillService;
	private final TeamRecommendationCandidateRepository candidateRepository;
	private final TeamApplicationProperties applicationProperties;
	private final TeamPositionService positionService;
	private final TeamPositionRanker positionRanker;
	private final TeamMemberRepository memberRepository;
	private final TeamMemberVisibilityService memberVisibilityService;
	private final ProfilePhotoService photoService;

	@Transactional(readOnly = true)
	public List<TeamRecommendation> findForUser(
			@NotNull UUID userId, @NotNull @Size(max = 50) List<@NotNull UUID> teamIds,
			@NotNull LocalDateTime now
	) {
		accountAccessService.ensureAccessAllowed(userId);
		if (teamIds.isEmpty()) {
			return List.of();
		}
		UUID viewerId = profileService.findOptionalIdByUserId(userId).orElse(null);
		Set<UUID> orderedIds = new LinkedHashSet<>(teamIds);
		var links = candidateRepository.findEligibleForTeams(viewerId, now,
				now.minus(applicationProperties.reapplicationCooldown()), orderedIds);
		if (links.isEmpty()) {
			return List.of();
		}
		Map<UUID, TeamRequestPosition> byPosition = links.stream().collect(Collectors.toMap(
				link -> link.getPosition().getId(), Function.identity(),
				(left, right) -> PUBLICATION_ORDER.compare(left, right) <= 0 ? left : right));
		Map<UUID, List<TeamRequestPosition>> byTeam = byPosition.values().stream()
				.collect(Collectors.groupingBy(link -> link.getRequest().getTeam().getId()));
		Map<UUID, TeamPositionResponse> positions = positionService.toResponses(
				byPosition.values().stream().map(TeamRequestPosition::getPosition).toList())
				.stream().collect(Collectors.toMap(TeamPositionResponse::id, Function.identity()));
		Set<UUID> profileSkills = viewerId == null ? Set.of() : profileSkillService.findSkillIds(viewerId);
		var members = visibleMembers(byTeam.keySet(), viewerId);
		List<TeamRecommendation> recommendations = new ArrayList<>();
		for (UUID teamId : orderedIds) {
			var teamLinks = byTeam.get(teamId);
			if (teamLinks == null) {
				continue;
			}
			Team team = teamLinks.getFirst().getRequest().getTeam();
			var teamMembers = members.getOrDefault(teamId, List.of());
			if (teamMembers.stream().noneMatch(member ->
					member.profileId().equals(team.getOwnerProfile().getId()))) {
				continue;
			}
			toRecommendation(team, teamMembers, teamLinks, positions, profileSkills).ifPresent(recommendations::add);
		}
		return List.copyOf(recommendations);
	}

	private Map<UUID, List<TeamMemberResponse>> visibleMembers(Set<UUID> teamIds, UUID viewerId) {
		var members = memberVisibilityService.filter(
				memberRepository.findByTeamIdInOrderByTeamIdAscJoinedAtAscProfileIdAsc(teamIds),
				viewerId);
		var photos = photoService.findReadyPhotosByProfileIds(members.stream()
				.map(member -> member.getProfile().getId()).distinct().toList());
		return members.stream().collect(Collectors.groupingBy(
				member -> member.getTeam().getId(),
				Collectors.mapping(member -> TeamMemberResponse.from(member,
						photos.getOrDefault(member.getProfile().getId(), List.of())), Collectors.toList())));
	}

	private Optional<TeamRecommendation> toRecommendation(
			Team team, List<TeamMemberResponse> members, List<TeamRequestPosition> links,
			Map<UUID, TeamPositionResponse> positions, Set<UUID> profileSkills
	) {
		Map<UUID, TeamRequestPosition> available = links.stream()
				.filter(link -> positions.get(link.getPosition().getId()).remainingSlotsCount() > 0)
				.collect(Collectors.toMap(link -> link.getPosition().getId(), Function.identity()));
		var candidates = available.values().stream().map(link -> {
			TeamPosition position = link.getPosition();
			Set<UUID> skills = positions.get(position.getId()).skills().stream()
					.map(SkillResponse::id).collect(Collectors.toSet());
			return new TeamPositionCandidate(position.getId(), position.getCreatedAt(), skills);
		}).toList();
		var ranked = positionRanker.rank(profileSkills, candidates);
		if (ranked.isEmpty()) {
			return Optional.empty();
		}
		List<TeamRecommendationPositionResponse> options = ranked.stream().map(result -> {
			UUID positionId = result.position().positionId();
			TeamRequestPosition link = available.get(positionId);
			return new TeamRecommendationPositionResponse(
					TeamRecommendationRequestResponse.from(link.getRequest()),
					TeamRequestPositionResponse.from(link, positions.get(positionId)),
					result.matchedSkillIds(), result.unmatchedSkillIds());
		}).toList();
		return Optional.of(new TeamRecommendation(
				TeamRecommendationTeamResponse.from(team, members),
				ranked.getFirst().position().positionId(), options));
	}
}
