package ru.itmo.nemat.weezzy.team.application;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.itmo.nemat.weezzy.common.dto.PageResponse;
import ru.itmo.nemat.weezzy.outbox.OutboxEventService;
import ru.itmo.nemat.weezzy.outbox.payload.TeamApplicationChangedPayload;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.team.application.dto.CreateTeamApplicationRequest;
import ru.itmo.nemat.weezzy.team.application.dto.TeamApplicationResponse;
import ru.itmo.nemat.weezzy.team.member.TeamMemberService;
import ru.itmo.nemat.weezzy.team.request.TeamRequestService;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPosition;
import ru.itmo.nemat.weezzy.team.request.position.TeamRequestPositionNotFoundException;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Validated
public class TeamApplicationService {
	private final TeamApplicationRepository repository;
	private final TeamRequestService requestService;
	private final TeamMemberService memberService;
	private final ProfileService profileService;
	private final AccountAccessService accountAccessService;
	private final OutboxEventService outboxEventService;

	@Transactional
	public TeamApplicationResponse send(
			UUID userId, UUID positionId, @Valid CreateTeamApplicationRequest request
	) {
		UUID profileId = profileService.findIdByUserId(userId);
		UUID requestId = requestService.findRequestIdByPosition(positionId);
		requestService.lockForInteraction(userId, requestId, List.of(profileId));
		requestService.requireReadableForUser(userId, requestId);
		TeamRequestPosition position = requestService.findPosition(positionId);
		Profile applicant = profileService.findById(profileId);
		ensureEligible(position, applicant, userId);
		if (repository.existsByPositionIdAndApplicantProfileIdAndStatus(
				positionId, profileId, TeamApplicationStatus.PENDING)) {
			throw new DuplicatePendingTeamApplicationException();
		}
		TeamApplication application = new TeamApplication();
		application.setPosition(position);
		application.setApplicantProfile(applicant);
		String message = request.message();
		application.setMessage(message == null || message.isBlank() ? null : message.strip());
		repository.saveAndFlush(application);
		publish(application);
		return TeamApplicationResponse.from(application);
	}

	@Transactional(readOnly = true)
	public TeamApplicationResponse findByIdForUser(UUID userId, UUID id) {
		accountAccessService.ensureAccessAllowed(userId);
		TeamApplicationParticipants participants = participants(id);
		if (!isUser(participants.ownerProfileId(), userId)
				&& !isUser(participants.applicantProfileId(), userId)) {
			throw new TeamApplicationNotFoundException(id);
		}
		return TeamApplicationResponse.from(repository.findById(id)
				.orElseThrow(() -> new TeamApplicationNotFoundException(id)));
	}

	@Transactional(readOnly = true)
	public PageResponse<TeamApplicationResponse> findOwn(
			UUID userId, TeamApplicationStatus status, Pageable pageable
	) {
		accountAccessService.ensureAccessAllowed(userId);
		return PageResponse.from(repository.findOwn(
				profileService.findIdByUserId(userId), status, pageable
		).map(TeamApplicationResponse::from));
	}

	@Transactional(readOnly = true)
	public PageResponse<TeamApplicationResponse> findForRequest(
			UUID userId, UUID requestId, UUID positionId,
			TeamApplicationStatus status, Pageable pageable
	) {
		requestService.requireOwner(userId, requestId);
		if (positionId != null
				&& !requestService.findRequestIdByPosition(positionId).equals(requestId)) {
			throw new TeamRequestPositionNotFoundException(positionId);
		}
		return PageResponse.from(repository.findForRequest(
				requestId, positionId, status, pageable
		).map(TeamApplicationResponse::from));
	}

	@Transactional
	public TeamApplicationResponse accept(UUID userId, UUID id) {
		return resolve(userId, id, TeamApplicationStatus.ACCEPTED);
	}

	@Transactional
	public TeamApplicationResponse decline(UUID userId, UUID id) {
		return resolve(userId, id, TeamApplicationStatus.DECLINED);
	}

	@Transactional
	public TeamApplicationResponse withdraw(UUID userId, UUID id) {
		return resolve(userId, id, TeamApplicationStatus.WITHDRAWN);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public TeamApplication findForNotification(UUID id, TeamApplicationStatus status) {
		TeamApplicationParticipants participants = repository.findParticipants(id).orElse(null);
		if (participants == null) {
			return null;
		}
		requestService.lockForInteraction(null, participants.requestId(),
				List.of(participants.applicantProfileId()));
		return repository.findByIdForUpdate(id)
				.filter(application -> application.getStatus() == status).orElse(null);
	}

	private TeamApplicationResponse resolve(
			UUID userId, UUID id, TeamApplicationStatus status
	) {
		TeamApplicationParticipants participants = participants(id);
		UUID actorId = status == TeamApplicationStatus.WITHDRAWN
				? participants.applicantProfileId() : participants.ownerProfileId();
		if (!isUser(actorId, userId)) {
			throw new TeamApplicationNotFoundException(id);
		}
		requestService.lockForInteraction(userId, participants.requestId(),
				List.of(participants.applicantProfileId()));
		TeamApplication application = repository.findByIdForUpdate(id)
				.orElseThrow(() -> new TeamApplicationNotFoundException(id));
		if (application.getStatus() == status) {
			return TeamApplicationResponse.from(application);
		}
		if (application.getStatus() != TeamApplicationStatus.PENDING) {
			throw new TeamApplicationStatusConflictException(application.getStatus());
		}
		if (status == TeamApplicationStatus.ACCEPTED) {
			TeamRequestPosition position = application.getPosition();
			ensureEligible(position, application.getApplicantProfile(), userId);
			memberService.add(position.getRequest().getTeam(),
					application.getApplicantProfile(), position.getPosition());
		}
		application.setStatus(status);
		application.setResolvedAt(LocalDateTime.now());
		repository.saveAndFlush(application);
		publish(application);
		return TeamApplicationResponse.from(application);
	}

	private void ensureEligible(TeamRequestPosition position, Profile applicant, UUID userId) {
		requestService.ensureAccepting(position.getRequest(), userId);
		memberService.ensureCanJoin(position.getRequest().getTeam(), applicant, position.getPosition());
	}

	private boolean isUser(UUID profileId, UUID userId) {
		return profileService.findOptionalOwnerUserId(profileId).filter(userId::equals)
				.isPresent();
	}

	private TeamApplicationParticipants participants(UUID id) {
		return repository.findParticipants(id)
				.orElseThrow(() -> new TeamApplicationNotFoundException(id));
	}

	private void publish(TeamApplication application) {
		outboxEventService.publish(new TeamApplicationChangedPayload(
				application.getId(), application.getStatus()));
	}
}
