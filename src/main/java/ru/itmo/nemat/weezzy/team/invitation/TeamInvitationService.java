package ru.itmo.nemat.weezzy.team.invitation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.common.dto.PageResponse;
import ru.itmo.nemat.weezzy.outbox.OutboxEventService;
import ru.itmo.nemat.weezzy.outbox.payload.TeamInvitationResolvedPayload;
import ru.itmo.nemat.weezzy.outbox.payload.TeamInvitationSentPayload;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.lock.AccountProfileLockService;
import ru.itmo.nemat.weezzy.team.Team;
import ru.itmo.nemat.weezzy.team.TeamNotFoundException;
import ru.itmo.nemat.weezzy.team.TeamAccessService;
import ru.itmo.nemat.weezzy.team.TeamRepository;
import ru.itmo.nemat.weezzy.team.invitation.dto.TeamInvitationResponse;
import ru.itmo.nemat.weezzy.team.member.TeamMemberService;
import ru.itmo.nemat.weezzy.team.position.TeamPosition;
import ru.itmo.nemat.weezzy.team.position.TeamPositionService;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamInvitationService {
	private final TeamInvitationRepository repository;
	private final TeamRepository teamRepository;
	private final TeamMemberService memberService;
	private final ProfileService profileService;
	private final AccountAccessService accountAccessService;
	private final TeamAccessService accessService;
	private final AccountProfileLockService lockService;
	private final TeamPositionService positionService;
	private final OutboxEventService outboxEventService;

	@Transactional
	public TeamInvitationResponse send(
			UUID userId, UUID teamId, UUID inviteeProfileId, UUID positionId
	) {
		Team team = accessService.requireOwnedForUpdate(
				userId, teamId, List.of(inviteeProfileId)
		);
		UUID ownerId = team.getOwnerProfile().getId();
		if (ownerId.equals(inviteeProfileId)) {
			throw new SelfTeamInvitationException();
		}
		Profile inviter = profileService.findById(ownerId);
		Profile invitee = profileService.findById(inviteeProfileId);
		TeamPosition position = positionService.findForTeam(teamId, positionId);
		memberService.ensureCanJoin(team, invitee, position);
		if (repository.existsByTeamIdAndInviteeProfileIdAndStatus(
				teamId, inviteeProfileId, TeamInvitationStatus.PENDING
		)) {
			throw new DuplicatePendingTeamInvitationException();
		}
		TeamInvitation invitation = new TeamInvitation();
		invitation.setTeam(team);
		invitation.setPosition(position);
		invitation.setInviterProfile(inviter);
		invitation.setInviteeProfile(invitee);
		repository.saveAndFlush(invitation);
		outboxEventService.publish(new TeamInvitationSentPayload(invitation.getId()));
		return TeamInvitationResponse.from(invitation);
	}

	@Transactional(readOnly = true)
	public TeamInvitationResponse findByIdForUser(UUID userId, UUID id) {
		accountAccessService.ensureAccessAllowed(userId);
		TeamInvitation invitation = repository.findById(id)
				.orElseThrow(() -> new TeamInvitationNotFoundException(id));
		boolean isInviter = profileService.findOptionalOwnerUserId(
				invitation.getInviterProfile().getId()
		).filter(userId::equals).isPresent();
		boolean isInvitee = profileService.findOptionalOwnerUserId(
				invitation.getInviteeProfile().getId()
		).filter(userId::equals).isPresent();
		if (!isInviter && !isInvitee) {
			throw new TeamInvitationNotFoundException(id);
		}
		return TeamInvitationResponse.from(invitation);
	}

	@Transactional(readOnly = true)
	public PageResponse<TeamInvitationResponse> findIncoming(
			UUID userId, TeamInvitationStatus status, Pageable pageable
	) {
		accountAccessService.ensureAccessAllowed(userId);
		return PageResponse.from(repository.findIncoming(
				userId, status, LocalDateTime.now(), pageable
		).map(TeamInvitationResponse::from));
	}

	@Transactional
	public TeamInvitationResponse accept(UUID userId, UUID invitationId) {
		return resolve(userId, invitationId, TeamInvitationStatus.ACCEPTED);
	}

	@Transactional
	public TeamInvitationResponse decline(UUID userId, UUID invitationId) {
		return resolve(userId, invitationId, TeamInvitationStatus.DECLINED);
	}

	@Transactional
	public TeamInvitationResponse cancel(UUID userId, UUID invitationId) {
		return resolve(userId, invitationId, TeamInvitationStatus.CANCELLED);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public TeamInvitation findForNotification(UUID id, TeamInvitationStatus status) {
		TeamInvitationParticipants participants = repository.findParticipants(id).orElse(null);
		if (participants == null) {
			return null;
		}
		lockService.lock(List.of(
				participants.inviterProfileId(), participants.inviteeProfileId()
		), null);
		if (teamRepository.findByIdForUpdate(participants.teamId()).isEmpty()) {
			return null;
		}
		return repository.findByIdForUpdate(id)
				.filter(invitation -> invitation.getStatus() == status)
				.orElse(null);
	}

	private TeamInvitationResponse resolve(
			UUID userId, UUID id, TeamInvitationStatus status
	) {
		TeamInvitationParticipants participants = repository.findParticipants(id)
				.orElseThrow(() -> new TeamInvitationNotFoundException(id));
		UUID actorProfileId = status == TeamInvitationStatus.CANCELLED
				? participants.inviterProfileId() : participants.inviteeProfileId();
		if (!profileService.findOptionalOwnerUserId(actorProfileId).filter(userId::equals)
				.isPresent()) {
			throw new TeamInvitationNotFoundException(id);
		}
		lockService.lock(List.of(
				participants.inviterProfileId(), participants.inviteeProfileId()
		), userId);
		Team team = teamRepository.findByIdForUpdate(participants.teamId())
				.orElseThrow(() -> new TeamNotFoundException(participants.teamId()));
		TeamInvitation invitation = repository.findByIdForUpdate(id)
				.orElseThrow(() -> new TeamInvitationNotFoundException(id));
		accountAccessService.ensureAccessAllowed(userId);
		if (invitation.getStatus() == status) {
			return TeamInvitationResponse.from(invitation);
		}
		if (invitation.getStatus() != TeamInvitationStatus.PENDING) {
			throw new TeamInvitationStatusConflictException(invitation.getStatus());
		}
		if (status == TeamInvitationStatus.ACCEPTED) {
			memberService.ensureCanJoin(team, invitation.getInviteeProfile(), invitation.getPosition());
			memberService.add(team, invitation.getInviteeProfile(), invitation.getPosition());
		}
		invitation.setStatus(status);
		invitation.setResolvedAt(LocalDateTime.now());
		repository.saveAndFlush(invitation);
		outboxEventService.publish(new TeamInvitationResolvedPayload(id, status));
		return TeamInvitationResponse.from(invitation);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void cancelPending(UUID teamId, UUID profileId) {
		teamRepository.findByIdForUpdate(teamId)
				.orElseThrow(() -> new TeamNotFoundException(teamId));
		LocalDateTime now = LocalDateTime.now();
		for (TeamInvitation invitation : repository.findPendingForUpdate(teamId, profileId)) {
			invitation.setStatus(TeamInvitationStatus.CANCELLED);
			invitation.setResolvedAt(now);
			outboxEventService.publish(new TeamInvitationResolvedPayload(
					invitation.getId(), TeamInvitationStatus.CANCELLED
			));
		}
		repository.flush();
	}

}
