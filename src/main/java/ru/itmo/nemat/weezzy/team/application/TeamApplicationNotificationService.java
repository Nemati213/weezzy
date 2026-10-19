package ru.itmo.nemat.weezzy.team.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.notification.NotificationService;
import ru.itmo.nemat.weezzy.notification.NotificationType;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.team.TeamStatus;
import ru.itmo.nemat.weezzy.team.member.TeamMemberService;
import ru.itmo.nemat.weezzy.team.request.TeamRequest;
import ru.itmo.nemat.weezzy.team.request.TeamRequestStatus;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamApplicationNotificationService {
	private final TeamApplicationService applicationService;
	private final TeamMemberService memberService;
	private final NotificationService notificationService;
	private final AccountAccessService accountAccessService;
	private final ProfileBlockService blockService;

	@Transactional
	public void notifyChanged(UUID id, TeamApplicationStatus status, UUID eventId) {
		TeamApplication application = applicationService.findForNotification(id, status);
		if (application == null || !isAvailable(application)) {
			return;
		}
		TeamRequest request = application.getPosition().getRequest();
		Profile recipient = status == TeamApplicationStatus.PENDING
				|| status == TeamApplicationStatus.WITHDRAWN
				? request.getTeam().getOwnerProfile() : application.getApplicantProfile();
		NotificationType type = switch (status) {
			case PENDING -> NotificationType.TEAM_APPLICATION_RECEIVED;
			case ACCEPTED -> NotificationType.TEAM_APPLICATION_ACCEPTED;
			case DECLINED -> NotificationType.TEAM_APPLICATION_DECLINED;
			case WITHDRAWN -> NotificationType.TEAM_APPLICATION_WITHDRAWN;
		};
		notificationService.createIfAbsent(recipient.getUser().getId(), type, Map.of(
				"applicationId", id,
				"teamId", request.getTeam().getId(),
				"requestId", request.getId(),
				"positionId", application.getPosition().getId(),
				"teamPositionId", application.getPosition().getPosition().getId(),
				"status", status
		), eventId);
	}

	private boolean isAvailable(TeamApplication application) {
		TeamRequest request = application.getPosition().getRequest();
		Profile owner = request.getTeam().getOwnerProfile();
		Profile applicant = application.getApplicantProfile();
		if (owner.getStatus() != ProfileStatus.ACTIVE
				|| applicant.getStatus() != ProfileStatus.ACTIVE
				|| owner.getUser() == null || applicant.getUser() == null) {
			return false;
		}
		if (application.getStatus() == TeamApplicationStatus.PENDING) {
			if (request.getTeam().getStatus() != TeamStatus.ACTIVE
					|| request.getStatus() != TeamRequestStatus.OPEN
					|| !request.getRecruitmentDeadline().isAfter(LocalDateTime.now())
					|| !memberService.isMember(request.getTeam().getId(), owner.getId())
					|| memberService.isMember(request.getTeam().getId(), applicant.getId())
					|| memberService.countByPosition(application.getPosition().getPosition().getId())
							>= application.getPosition().getPosition().getCapacity()) {
				return false;
			}
		} else if (application.getStatus() == TeamApplicationStatus.ACCEPTED
				&& request.getTeam().getStatus() != TeamStatus.ACTIVE) {
			return false;
		}
		return accountAccessService.findRestrictedUserIds(Set.of(
				owner.getUser().getId(), applicant.getUser().getId()
		)).isEmpty() && !blockService.isBlockedBetween(owner.getId(), applicant.getId());
	}
}
