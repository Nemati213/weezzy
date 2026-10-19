package ru.itmo.nemat.weezzy.team.position;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.skill.SkillService;
import ru.itmo.nemat.weezzy.skill.dto.SkillResponse;
import ru.itmo.nemat.weezzy.team.Team;
import ru.itmo.nemat.weezzy.team.TeamAccessService;
import ru.itmo.nemat.weezzy.team.TeamProfileNotActiveException;
import ru.itmo.nemat.weezzy.team.member.TeamMemberService;
import ru.itmo.nemat.weezzy.team.position.dto.CreateTeamPositionRequest;
import ru.itmo.nemat.weezzy.team.position.dto.UpdateTeamPositionRequest;
import ru.itmo.nemat.weezzy.team.position.dto.TeamPositionResponse;
import ru.itmo.nemat.weezzy.team.position.skill.TeamPositionSkill;
import ru.itmo.nemat.weezzy.team.position.skill.TeamPositionSkillId;
import ru.itmo.nemat.weezzy.team.position.skill.TeamPositionSkillRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Validated
public class TeamPositionService {
	private final TeamPositionRepository repository;
	private final TeamPositionSkillRepository skillRepository;
	private final TeamAccessService accessService;
	private final TeamMemberService memberService;
	private final SkillService skillService;

	@Transactional
	public TeamPositionResponse createForUser(
			UUID userId, UUID teamId, @Valid CreateTeamPositionRequest request
	) {
		Team team = accessService.requireOwnedForUpdate(userId, teamId);
		ensureEditable(team);
		TeamPosition position = create(team, request.name(), request.description(),
				request.capacity(), request.skillIds());
		return toResponses(List.of(position)).getFirst();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public TeamPosition createFounderPosition(Team team, String name) {
		return create(team, name, null, 1, Set.of());
	}

	@Transactional
	public TeamPositionResponse updateForUser(
			UUID userId, UUID teamId, UUID positionId, @Valid UpdateTeamPositionRequest request
	) {
		Team team = accessService.requireOwnedForUpdate(userId, teamId);
		ensureEditable(team);
		TeamPosition position = findForTeam(teamId, positionId);
		if (request.capacity() < memberService.countByPosition(positionId)) {
			throw new TeamPositionCapacityException();
		}
		position.setName(request.name().strip());
		position.setDescription(normalize(request.description()));
		position.setCapacity(request.capacity());
		skillRepository.deleteAll(skillRepository.findByPositionIdOrderBySkillIdAsc(positionId));
		skillRepository.flush();
		setSkills(position, request.skillIds());
		repository.flush();
		return toResponses(List.of(position)).getFirst();
	}

	@Transactional(readOnly = true)
	public List<TeamPositionResponse> findForUser(UUID userId, UUID teamId) {
		accessService.requireVisibleForUser(userId, teamId);
		return findResponsesByTeam(teamId);
	}

	@Transactional(readOnly = true)
	public TeamPositionResponse findByIdForUser(UUID userId, UUID teamId, UUID positionId) {
		accessService.requireVisibleForUser(userId, teamId);
		return toResponses(List.of(findForTeam(teamId, positionId))).getFirst();
	}

	@Transactional(readOnly = true)
	public List<TeamPositionResponse> findResponsesByTeam(UUID teamId) {
		return toResponses(repository.findByTeamIdOrderByCreatedAtAscIdAsc(teamId));
	}

	@Transactional(readOnly = true)
	public TeamPosition findForTeam(UUID teamId, UUID id) {
		return repository.findById(id).filter(position -> position.getTeam().getId().equals(teamId))
				.orElseThrow(() -> new TeamPositionNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public List<TeamPosition> findAllForTeam(UUID teamId, Collection<UUID> ids) {
		List<TeamPosition> positions = repository.findByIdInAndTeamId(ids, teamId);
		Set<UUID> found = positions.stream().map(TeamPosition::getId)
				.collect(java.util.stream.Collectors.toSet());
		ids.stream().filter(id -> !found.contains(id)).findFirst()
				.ifPresent(id -> { throw new TeamPositionNotFoundException(id); });
		return positions;
	}

	@Transactional(readOnly = true)
	public List<TeamPositionResponse> toResponses(List<TeamPosition> positions) {
		if (positions.isEmpty()) {
			return List.of();
		}
		List<UUID> ids = positions.stream().map(TeamPosition::getId).distinct().toList();
		Map<UUID, Long> filled = memberService.countByPositions(ids);
		Map<UUID, List<SkillResponse>> skills = new HashMap<>();
		for (TeamPositionSkill link : skillRepository
				.findByPositionIdInOrderByPositionIdAscSkillIdAsc(ids)) {
			skills.computeIfAbsent(link.getPosition().getId(), id -> new ArrayList<>())
					.add(SkillResponse.from(link.getSkill()));
		}
		return positions.stream().map(position -> TeamPositionResponse.from(position,
				filled.getOrDefault(position.getId(), 0L),
				skills.getOrDefault(position.getId(), List.of()))).toList();
	}

	private TeamPosition create(
			Team team, String name, String description, int capacity, Set<UUID> skills
	) {
		TeamPosition position = new TeamPosition();
		position.setTeam(team);
		position.setName(name.strip());
		position.setDescription(normalize(description));
		position.setCapacity(capacity);
		repository.saveAndFlush(position);
		setSkills(position, skills);
		return position;
	}

	private void setSkills(TeamPosition position, Set<UUID> ids) {
		skillRepository.saveAll(skillService.findAllByIds(ids).stream().map(skill -> {
			TeamPositionSkill link = new TeamPositionSkill();
			link.setId(new TeamPositionSkillId(position.getId(), skill.getId()));
			link.setPosition(position);
			link.setSkill(skill);
			return link;
		}).toList());
	}

	private void ensureEditable(Team team) {
		accessService.ensureActive(team);
		if (team.getOwnerProfile().getStatus() != ProfileStatus.ACTIVE) {
			throw new TeamProfileNotActiveException(team.getOwnerProfile().getId());
		}
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
