package ru.itmo.nemat.weezzy.team.request;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.itmo.nemat.weezzy.common.dto.PageResponse;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.skill.Skill;
import ru.itmo.nemat.weezzy.skill.SkillService;
import ru.itmo.nemat.weezzy.skill.dto.SkillResponse;
import ru.itmo.nemat.weezzy.team.Team;
import ru.itmo.nemat.weezzy.team.TeamAccessService;
import ru.itmo.nemat.weezzy.team.TeamNotFoundException;
import ru.itmo.nemat.weezzy.team.member.TeamMemberId;
import ru.itmo.nemat.weezzy.team.member.TeamMemberRepository;
import ru.itmo.nemat.weezzy.team.request.dto.CreateRecruitmentRequest;
import ru.itmo.nemat.weezzy.team.request.dto.TeamRequestResponse;
import ru.itmo.nemat.weezzy.team.request.dto.UpdateRecruitmentRequest;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPosition;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPositionRepository;
import ru.itmo.nemat.weezzy.team.request.position.dto.CreateRecruitmentPositionRequest;
import ru.itmo.nemat.weezzy.team.request.position.dto.TeamRequestPositionResponse;
import ru.itmo.nemat.weezzy.team.request.position.skill.TeamRequestPositionSkill;
import ru.itmo.nemat.weezzy.team.request.position.skill.TeamRequestPositionSkillId;
import ru.itmo.nemat.weezzy.team.request.position.skill.TeamRequestPositionSkillRepository;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Validated
public class TeamRequestService {
	private final TeamRequestRepository requestRepository;
	private final TeamRequestPositionRepository positionRepository;
	private final TeamRequestPositionSkillRepository positionSkillRepository;
	private final TeamMemberRepository memberRepository;
	private final TeamAccessService teamAccessService;
	private final ProfileService profileService;
	private final SkillService skillService;
	private final AccountAccessService accountAccessService;

	@Transactional
	public TeamRequestResponse createForUser(
			UUID userId, UUID teamId, @Valid CreateRecruitmentRequest request
	) {
		Team team = teamAccessService.requireOwnedForUpdate(userId, teamId);
		ensureRecruitingAllowed(team);
		validateDeadline(request.recruitmentDeadline());
		validateSize(teamId, request.targetTeamSize(), request.positions().stream()
				.mapToLong(CreateRecruitmentPositionRequest::slotsCount).sum());
		Set<UUID> skillIds = request.positions().stream()
				.flatMap(position -> position.skillIds().stream()).collect(Collectors.toSet());
		Map<UUID, Skill> skills = skillService.findAllByIds(skillIds).stream()
				.collect(Collectors.toMap(Skill::getId, Function.identity()));

		TeamRequest recruitment = new TeamRequest();
		recruitment.setTeam(team);
		recruitment.setTitle(request.title().strip());
		recruitment.setDescription(normalizeDescription(request.description()));
		recruitment.setRecruitmentDeadline(request.recruitmentDeadline());
		recruitment.setTargetTeamSize(request.targetTeamSize());
		requestRepository.save(recruitment);

		List<TeamRequestPositionSkill> links = new ArrayList<>();
		for (CreateRecruitmentPositionRequest positionRequest : request.positions()) {
			TeamRequestPosition position = new TeamRequestPosition();
			position.setRequest(recruitment);
			position.setName(positionRequest.name().strip());
			position.setDescription(normalizeDescription(positionRequest.description()));
			position.setSlotsCount(positionRequest.slotsCount());
			positionRepository.save(position);
			for (UUID skillId : positionRequest.skillIds()) {
				TeamRequestPositionSkill link = new TeamRequestPositionSkill();
				link.setId(new TeamRequestPositionSkillId(position.getId(), skillId));
				link.setPosition(position);
				link.setSkill(skills.get(skillId));
				links.add(link);
			}
		}
		positionSkillRepository.saveAll(links);
		requestRepository.flush();
		return toResponse(recruitment);
	}

	@Transactional(readOnly = true)
	public TeamRequestResponse findByIdForUser(UUID userId, UUID requestId) {
		accountAccessService.ensureAccessAllowed(userId);
		TeamRequest request = requestRepository.findById(requestId)
				.orElseThrow(() -> new TeamRequestNotFoundException(requestId));
		UUID viewerProfileId = profileService.findOptionalIdByUserId(userId).orElse(null);
		if (!request.getTeam().isOwnedBy(viewerProfileId)) {
			if (request.getStatus() != TeamRequestStatus.OPEN
					|| !request.getRecruitmentDeadline().isAfter(LocalDateTime.now())) {
				throw new TeamRequestNotFoundException(requestId);
			}
			try {
				teamAccessService.requireVisibleForUser(userId, request.getTeam().getId());
			} catch (TeamNotFoundException exception) {
				throw new TeamRequestNotFoundException(requestId);
			}
		}
		return toResponse(request);
	}

	@Transactional(readOnly = true)
	public PageResponse<TeamRequestResponse> findByTeamForUser(
			UUID userId, UUID teamId, TeamRequestStatus status, Pageable pageable
	) {
		teamAccessService.requireOwnedByUser(userId, teamId);
		var page = status == null
				? requestRepository.findByTeamId(teamId, pageable)
				: requestRepository.findByTeamIdAndStatus(teamId, status, pageable);
		Map<UUID, TeamRequestResponse> responses = toResponses(page.getContent()).stream()
				.collect(Collectors.toMap(TeamRequestResponse::id, Function.identity()));
		return PageResponse.from(page.map(request -> responses.get(request.getId())));
	}

	@Transactional
	public TeamRequestResponse updateForUser(
			UUID userId, UUID requestId, @Valid UpdateRecruitmentRequest update
	) {
		TeamRequest request = findOwnedForUpdate(userId, requestId);
		if (request.getStatus() != TeamRequestStatus.OPEN) {
			throw new TeamRequestClosedException(requestId);
		}
		ensureRecruitingAllowed(request.getTeam());
		validateDeadline(update.recruitmentDeadline());
		long slots = positionRepository
				.findByRequestIdOrderByCreatedAtAscIdAsc(requestId).stream()
				.mapToLong(TeamRequestPosition::getSlotsCount).sum();
		validateSize(request.getTeam().getId(), update.targetTeamSize(), slots);
		request.setTitle(update.title().strip());
		request.setDescription(normalizeDescription(update.description()));
		request.setRecruitmentDeadline(update.recruitmentDeadline());
		request.setTargetTeamSize(update.targetTeamSize());
		requestRepository.flush();
		return toResponse(request);
	}

	@Transactional
	public TeamRequestResponse closeForUser(UUID userId, UUID requestId) {
		TeamRequest request = findOwnedForUpdate(userId, requestId);
		if (request.getStatus() != TeamRequestStatus.CLOSED) {
			request.setStatus(TeamRequestStatus.CLOSED);
			requestRepository.flush();
		}
		return toResponse(request);
	}

	private TeamRequest findOwnedForUpdate(UUID userId, UUID requestId) {
		UUID teamId = requestRepository.findTeamId(requestId)
				.orElseThrow(() -> new TeamRequestNotFoundException(requestId));
		teamAccessService.requireOwnedForUpdate(userId, teamId);
		TeamRequest request = requestRepository.findByIdForUpdate(requestId)
				.orElseThrow(() -> new TeamRequestNotFoundException(requestId));
		if (!teamId.equals(request.getTeam().getId())) {
			throw new TeamRequestNotFoundException(requestId);
		}
		return request;
	}

	private void ensureRecruitingAllowed(Team team) {
		teamAccessService.ensureActive(team);
		UUID ownerId = team.getOwnerProfile().getId();
		if (team.getOwnerProfile().getStatus() != ProfileStatus.ACTIVE) {
			throw new TeamRequestProfileNotActiveException(ownerId);
		}
		if (!memberRepository.existsById(new TeamMemberId(team.getId(), ownerId))) {
			throw new TeamNotFoundException(team.getId());
		}
	}

	private void validateDeadline(LocalDateTime deadline) {
		if (deadline == null || !deadline.isAfter(LocalDateTime.now())) {
			throw new InvalidRecruitmentDeadlineException();
		}
	}

	private void validateSize(UUID teamId, int targetSize, long slots) {
		long expectedSize = memberRepository.countByTeamId(teamId) + slots;
		if (targetSize != expectedSize) {
			throw new InvalidRecruitmentSizeException(expectedSize);
		}
	}

	private TeamRequestResponse toResponse(TeamRequest request) {
		return toResponses(List.of(request)).getFirst();
	}

	private List<TeamRequestResponse> toResponses(List<TeamRequest> requests) {
		if (requests.isEmpty()) {
			return List.of();
		}
		List<TeamRequestPosition> positions = positionRepository
				.findByRequestIdInOrderByCreatedAtAscIdAsc(
						requests.stream().map(TeamRequest::getId).toList());
		Map<UUID, List<SkillResponse>> skillsByPositionId = new HashMap<>();
		if (!positions.isEmpty()) {
			for (TeamRequestPositionSkill link : positionSkillRepository
					.findByPositionIdInOrderByPositionIdAscSkillIdAsc(
							positions.stream().map(TeamRequestPosition::getId).toList())) {
				skillsByPositionId.computeIfAbsent(link.getPosition().getId(),
						id -> new ArrayList<>()).add(SkillResponse.from(link.getSkill()));
			}
		}
		Map<UUID, List<TeamRequestPositionResponse>> positionsByRequestId = new HashMap<>();
		for (TeamRequestPosition position : positions) {
			positionsByRequestId.computeIfAbsent(position.getRequest().getId(),
					id -> new ArrayList<>()).add(TeamRequestPositionResponse.from(position,
					skillsByPositionId.getOrDefault(position.getId(), List.of())));
		}
		return requests.stream().map(request -> TeamRequestResponse.from(request,
				positionsByRequestId.getOrDefault(request.getId(), List.of()))).toList();
	}

	private String normalizeDescription(String description) {
		return description == null || description.isBlank() ? null : description.strip();
	}
}
