package ru.itmo.nemat.weezzy.team.request;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.itmo.nemat.weezzy.common.dto.PageResponse;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.team.Team;
import ru.itmo.nemat.weezzy.team.TeamAccessService;
import ru.itmo.nemat.weezzy.team.TeamNotFoundException;
import ru.itmo.nemat.weezzy.team.member.TeamMemberService;
import ru.itmo.nemat.weezzy.team.position.TeamPosition;
import ru.itmo.nemat.weezzy.team.position.TeamPositionService;
import ru.itmo.nemat.weezzy.team.position.dto.TeamPositionResponse;
import ru.itmo.nemat.weezzy.team.request.dto.CreateRecruitmentRequest;
import ru.itmo.nemat.weezzy.team.request.dto.TeamRequestResponse;
import ru.itmo.nemat.weezzy.team.request.dto.UpdateRecruitmentRequest;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPosition;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPositionNotFoundException;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPositionRepository;
import ru.itmo.nemat.weezzy.team.request.position.dto.TeamRequestPositionResponse;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Validated
public class TeamRequestService {
	private final TeamRequestRepository requestRepository;
	private final TeamRequestPositionRepository positionRepository;
	private final TeamPositionService positionService;
	private final TeamMemberService memberService;
	private final TeamAccessService teamAccessService;
	private final ProfileService profileService;
	private final AccountAccessService accountAccessService;

	@Transactional(readOnly = true)
	public PageResponse<TeamRequestResponse> searchForUser(
			UUID userId, String query, Set<UUID> skillIds, Pageable pageable
	) {
		accountAccessService.ensureAccessAllowed(userId);
		UUID viewerId = profileService.findOptionalIdByUserId(userId).orElse(null);
		Set<UUID> skills = skillIds == null ? Set.of() : Set.copyOf(skillIds);
		String text = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
		String pattern = "%" + text.replace("!", "!!")
				.replace("%", "!%").replace("_", "!_") + "%";
		var page = requestRepository.search(viewerId, LocalDateTime.now(), pattern,
				skills, skills.size(), pageable);
		Map<UUID, TeamRequestResponse> responses = toResponses(page.getContent()).stream()
				.collect(Collectors.toMap(TeamRequestResponse::id, Function.identity()));
		return PageResponse.from(page.map(request -> responses.get(request.getId())));
	}

	@Transactional
	public TeamRequestResponse createForUser(
			UUID userId, UUID teamId, @Valid CreateRecruitmentRequest input
	) {
		Team team = teamAccessService.requireOwnedForUpdate(userId, teamId);
		ensureRecruitingAllowed(team);
		validateDeadline(input.recruitmentDeadline());
		List<TeamPosition> positions = positionService.findAllForTeam(teamId, input.positionIds());
		positions.forEach(memberService::ensureCapacity);
		TeamRequest request = new TeamRequest();
		request.setTeam(team);
		request.setTitle(input.title().strip());
		request.setDescription(normalize(input.description()));
		request.setRecruitmentDeadline(input.recruitmentDeadline());
		requestRepository.save(request);
		for (TeamPosition position : positions) {
			TeamRequestPosition link = new TeamRequestPosition();
			link.setRequest(request);
			link.setPosition(position);
			positionRepository.save(link);
		}
		requestRepository.flush();
		return toResponses(List.of(request)).getFirst();
	}

	@Transactional(readOnly = true)
	public TeamRequestResponse findByIdForUser(UUID userId, UUID requestId) {
		return toResponses(List.of(requireReadableForUser(userId, requestId))).getFirst();
	}

	@Transactional(readOnly = true)
	public TeamRequest requireReadableForUser(UUID userId, UUID requestId) {
		accountAccessService.ensureAccessAllowed(userId);
		TeamRequest request = requestRepository.findById(requestId)
				.orElseThrow(() -> new TeamRequestNotFoundException(requestId));
		UUID profileId = profileService.findOptionalIdByUserId(userId).orElse(null);
		if (!request.getTeam().isOwnedBy(profileId)) {
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
		return request;
	}

	@Transactional(readOnly = true)
	public PageResponse<TeamRequestResponse> findByTeamForUser(
			UUID userId, UUID teamId, TeamRequestStatus status, Pageable pageable
	) {
		teamAccessService.requireOwnedByUser(userId, teamId);
		var page = status == null ? requestRepository.findByTeamId(teamId, pageable)
				: requestRepository.findByTeamIdAndStatus(teamId, status, pageable);
		Map<UUID, TeamRequestResponse> responses = toResponses(page.getContent()).stream()
				.collect(Collectors.toMap(TeamRequestResponse::id, Function.identity()));
		return PageResponse.from(page.map(request -> responses.get(request.getId())));
	}

	@Transactional
	public TeamRequestResponse updateForUser(
			UUID userId, UUID id, @Valid UpdateRecruitmentRequest input
	) {
		TeamRequest request = findOwnedForUpdate(userId, id);
		if (request.getStatus() != TeamRequestStatus.OPEN) {
			throw new TeamRequestClosedException(id);
		}
		ensureRecruitingAllowed(request.getTeam());
		validateDeadline(input.recruitmentDeadline());
		request.setTitle(input.title().strip());
		request.setDescription(normalize(input.description()));
		request.setRecruitmentDeadline(input.recruitmentDeadline());
		requestRepository.flush();
		return toResponses(List.of(request)).getFirst();
	}

	@Transactional
	public TeamRequestResponse closeForUser(UUID userId, UUID id) {
		TeamRequest request = findOwnedForUpdate(userId, id);
		request.setStatus(TeamRequestStatus.CLOSED);
		requestRepository.flush();
		return toResponses(List.of(request)).getFirst();
	}

	@Transactional(readOnly = true)
	public UUID findRequestIdByPosition(UUID id) {
		return positionRepository.findRequestId(id)
				.orElseThrow(() -> new TeamRequestPositionNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public TeamRequestPosition findPosition(UUID id) {
		return positionRepository.findById(id)
				.orElseThrow(() -> new TeamRequestPositionNotFoundException(id));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public TeamRequest lockForInteraction(
			UUID userId, UUID requestId, Collection<UUID> profileIds
	) {
		UUID teamId = requestRepository.findTeamId(requestId)
				.orElseThrow(() -> new TeamRequestNotFoundException(requestId));
		teamAccessService.lockForInteraction(userId, teamId, profileIds);
		return requestRepository.findByIdForUpdate(requestId)
				.orElseThrow(() -> new TeamRequestNotFoundException(requestId));
	}

	@Transactional(readOnly = true)
	public void requireOwner(UUID userId, UUID id) {
		UUID teamId = requestRepository.findTeamId(id)
				.orElseThrow(() -> new TeamRequestNotFoundException(id));
		teamAccessService.requireOwnedByUser(userId, teamId);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void ensureAccepting(TeamRequest request, UUID userId) {
		ensureRecruitingAllowed(request.getTeam());
		if (request.getStatus() != TeamRequestStatus.OPEN
				|| !request.getRecruitmentDeadline().isAfter(LocalDateTime.now())) {
			throw new TeamRequestClosedException(request.getId());
		}
		teamAccessService.requireVisibleForUser(userId, request.getTeam().getId());
	}

	private TeamRequest findOwnedForUpdate(UUID userId, UUID id) {
		UUID teamId = requestRepository.findTeamId(id)
				.orElseThrow(() -> new TeamRequestNotFoundException(id));
		teamAccessService.requireOwnedForUpdate(userId, teamId);
		return requestRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new TeamRequestNotFoundException(id));
	}

	private void ensureRecruitingAllowed(Team team) {
		teamAccessService.ensureActive(team);
		UUID ownerId = team.getOwnerProfile().getId();
		if (team.getOwnerProfile().getStatus() != ProfileStatus.ACTIVE) {
			throw new TeamRequestProfileNotActiveException(ownerId);
		}
		if (!memberService.isMember(team.getId(), ownerId)) {
			throw new TeamNotFoundException(team.getId());
		}
	}

	private List<TeamRequestResponse> toResponses(List<TeamRequest> requests) {
		if (requests.isEmpty()) {
			return List.of();
		}
		List<TeamRequestPosition> links = positionRepository
				.findByRequestIdInOrderByCreatedAtAscIdAsc(
						requests.stream().map(TeamRequest::getId).toList());
		Map<UUID, TeamPosition> positions = links.stream().map(TeamRequestPosition::getPosition)
				.collect(Collectors.toMap(TeamPosition::getId, Function.identity(), (a, b) -> a));
		Map<UUID, TeamPositionResponse> responses = positionService.toResponses(
				new ArrayList<>(positions.values())).stream()
				.collect(Collectors.toMap(TeamPositionResponse::id, Function.identity()));
		Map<UUID, List<TeamRequestPositionResponse>> byRequest = new HashMap<>();
		for (TeamRequestPosition link : links) {
			byRequest.computeIfAbsent(link.getRequest().getId(), id -> new ArrayList<>())
					.add(TeamRequestPositionResponse.from(link,
							responses.get(link.getPosition().getId())));
		}
		return requests.stream().map(request -> TeamRequestResponse.from(request,
				byRequest.getOrDefault(request.getId(), List.of()))).toList();
	}

	private void validateDeadline(LocalDateTime deadline) {
		if (deadline == null || !deadline.isAfter(LocalDateTime.now())) {
			throw new InvalidRecruitmentDeadlineException();
		}
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
