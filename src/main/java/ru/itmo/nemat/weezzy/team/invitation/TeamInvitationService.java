package ru.itmo.nemat.weezzy.team.invitation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.common.dto.PageResponse;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.outbox.OutboxEventService;
import ru.itmo.nemat.weezzy.outbox.payload.TeamInvitationResolvedPayload;
import ru.itmo.nemat.weezzy.outbox.payload.TeamInvitationSentPayload;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.team.Team;
import ru.itmo.nemat.weezzy.team.TeamNotActiveException;
import ru.itmo.nemat.weezzy.team.TeamNotFoundException;
import ru.itmo.nemat.weezzy.team.TeamAccessService;
import ru.itmo.nemat.weezzy.team.TeamRepository;
import ru.itmo.nemat.weezzy.team.TeamStatus;
import ru.itmo.nemat.weezzy.team.invitation.dto.TeamInvitationResponse;
import ru.itmo.nemat.weezzy.team.member.TeamMember;
import ru.itmo.nemat.weezzy.team.member.TeamMemberAlreadyExistsException;
import ru.itmo.nemat.weezzy.team.member.TeamMemberId;
import ru.itmo.nemat.weezzy.team.member.TeamMemberRepository;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamInvitationService {
	private final TeamInvitationRepository repository;
	private final TeamRepository teamRepository;
	private final TeamMemberRepository memberRepository;
	private final ProfileRepository profileRepository;
	private final ProfileService profileService;
	private final AccountAccessService accountAccessService;
	private final TeamAccessService accessService;
	private final ProfileBlockService blockService;
	private final OutboxEventService outboxEventService;

	@Transactional
	public TeamInvitationResponse send(UUID userId, UUID teamId, UUID inviteeProfileId) {
		UUID ownerId = teamRepository.findOwnerProfileId(teamId)
				.orElseThrow(() -> new TeamNotFoundException(teamId));
		accessService.ensureOwner(userId, ownerId);
		if (ownerId.equals(inviteeProfileId)) {
			throw new SelfTeamInvitationException();
		}
		accessService.lockProfiles(List.of(ownerId, inviteeProfileId), userId);
		Team team = accessService.findForUpdate(teamId);
		Profile inviter = profileService.findById(ownerId);
		Profile invitee = profileService.findById(inviteeProfileId);
		accountAccessService.ensureAccessAllowed(userId);
		ensureEligible(team, inviter, invitee);
		ensureNotMember(teamId, inviteeProfileId);
		if (repository.existsByTeamIdAndInviteeProfileIdAndStatus(
				teamId, inviteeProfileId, TeamInvitationStatus.PENDING
		)) {
			throw new DuplicatePendingTeamInvitationException();
		}
		TeamInvitation invitation = new TeamInvitation();
		invitation.setTeam(team);
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
		boolean isInviter = profileRepository.findOwnerUserId(
				invitation.getInviterProfile().getId()
		).filter(userId::equals).isPresent();
		boolean isInvitee = profileRepository.findOwnerUserId(
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
		accessService.lockProfiles(List.of(
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
		if (!profileRepository.findOwnerUserId(actorProfileId).filter(userId::equals)
				.isPresent()) {
			throw new TeamInvitationNotFoundException(id);
		}
		accessService.lockProfiles(List.of(
				participants.inviterProfileId(), participants.inviteeProfileId()
		), userId);
		Team team = accessService.findForUpdate(participants.teamId());
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
			ensureEligible(team, invitation.getInviterProfile(), invitation.getInviteeProfile());
			ensureNotMember(team.getId(), invitation.getInviteeProfile().getId());
			TeamMember member = new TeamMember();
			member.setId(new TeamMemberId(team.getId(), invitation.getInviteeProfile().getId()));
			member.setTeam(team);
			member.setProfile(invitation.getInviteeProfile());
			memberRepository.saveAndFlush(member);
		}
		invitation.setStatus(status);
		invitation.setResolvedAt(LocalDateTime.now());
		repository.saveAndFlush(invitation);
		outboxEventService.publish(new TeamInvitationResolvedPayload(id, status));
		return TeamInvitationResponse.from(invitation);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void cancelPending(UUID teamId, UUID profileId) {
		accessService.findForUpdate(teamId);
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

	private void ensureEligible(Team team, Profile inviter, Profile invitee) {
		if (team.getStatus() != TeamStatus.ACTIVE) {
			throw new TeamNotActiveException(team.getId());
		}
		if (!team.getOwnerProfile().getId().equals(inviter.getId())
				|| !memberRepository.existsById(new TeamMemberId(team.getId(), inviter.getId()))) {
			throw new TeamNotFoundException(team.getId());
		}
		if (inviter.getStatus() != ProfileStatus.ACTIVE
				|| invitee.getStatus() != ProfileStatus.ACTIVE
				|| inviter.getUser() == null || invitee.getUser() == null) {
			throw new TeamInvitationProfileNotActiveException();
		}
		profileService.ensureOwnerAccessAllowed(inviter.getId());
		profileService.ensureOwnerAccessAllowed(invitee.getId());
		blockService.ensureInteractionAllowed(inviter.getId(), invitee.getId());
	}

	private void ensureNotMember(UUID teamId, UUID profileId) {
		if (memberRepository.existsById(new TeamMemberId(teamId, profileId))) {
			throw new TeamMemberAlreadyExistsException();
		}
	}
}
