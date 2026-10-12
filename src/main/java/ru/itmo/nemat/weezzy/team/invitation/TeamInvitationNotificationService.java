package ru.itmo.nemat.weezzy.team.invitation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.notification.NotificationService;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.team.TeamStatus;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamInvitationNotificationService {
	private final TeamInvitationService invitationService;
	private final NotificationService notificationService;
	private final AccountAccessService accountAccessService;
	private final ProfileBlockService blockService;

	@Transactional
	public void notifySent(UUID invitationId, UUID eventId) {
		TeamInvitation invitation = invitationService.findForNotification(
				invitationId, TeamInvitationStatus.PENDING
		);
		if (isAvailable(invitation, false)) {
			create(invitation, invitation.getInviteeProfile(),
					NotificationType.TEAM_INVITATION_RECEIVED, eventId);
		}
	}

	@Transactional
	public void notifyResolved(UUID invitationId, TeamInvitationStatus status, UUID eventId) {
		TeamInvitation invitation = invitationService.findForNotification(invitationId, status);
		if (!isAvailable(invitation, status == TeamInvitationStatus.CANCELLED)) {
			return;
		}
		NotificationType type = switch (status) {
			case ACCEPTED -> NotificationType.TEAM_INVITATION_ACCEPTED;
			case DECLINED -> NotificationType.TEAM_INVITATION_DECLINED;
			case CANCELLED -> NotificationType.TEAM_INVITATION_CANCELLED;
			case PENDING -> throw new IllegalArgumentException("Invitation is not resolved");
		};
		Profile recipient = status == TeamInvitationStatus.CANCELLED
				? invitation.getInviteeProfile() : invitation.getInviterProfile();
		create(invitation, recipient, type, eventId);
	}

	private boolean isAvailable(TeamInvitation invitation, boolean allowArchived) {
		if (invitation == null) {
			return false;
		}
		TeamStatus teamStatus = invitation.getTeam().getStatus();
		if ((teamStatus != TeamStatus.ACTIVE
				&& !(allowArchived && teamStatus == TeamStatus.ARCHIVED))
				|| !invitation.getTeam().getOwnerProfile().getId()
						.equals(invitation.getInviterProfile().getId())) {
			return false;
		}
		Profile inviter = invitation.getInviterProfile();
		Profile invitee = invitation.getInviteeProfile();
		if (inviter.getStatus() != ProfileStatus.ACTIVE
				|| invitee.getStatus() != ProfileStatus.ACTIVE
				|| inviter.getUser() == null || invitee.getUser() == null) {
			return false;
		}
		return accountAccessService.findRestrictedUserIds(Set.of(
				inviter.getUser().getId(), invitee.getUser().getId()
		)).isEmpty() && !blockService.isBlockedBetween(inviter.getId(), invitee.getId());
	}

	private void create(
			TeamInvitation invitation, Profile recipient, NotificationType type, UUID eventId
	) {
		notificationService.createIfAbsent(recipient.getUser().getId(), type, Map.of(
				"invitationId", invitation.getId(),
				"teamId", invitation.getTeam().getId(),
				"status", invitation.getStatus()
		), eventId);
	}
}
