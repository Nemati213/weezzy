package ru.itmo.nemat.weezzy.team;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockService;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.profile.lock.AccountProfileLockService;
import ru.itmo.nemat.weezzy.team.member.TeamMemberId;
import ru.itmo.nemat.weezzy.team.member.TeamMemberRepository;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamAccessService {
	private final TeamRepository teamRepository;
	private final ProfileService profileService;
	private final AccountAccessService accountAccessService;
	private final AccountProfileLockService lockService;
	private final TeamMemberRepository memberRepository;
	private final ProfileBlockService blockService;

	@Transactional(readOnly = true)
	public Team requireVisibleForUser(UUID userId, UUID teamId) {
		accountAccessService.ensureAccessAllowed(userId);
		Team team = teamRepository.findByIdAndStatus(teamId, TeamStatus.ACTIVE)
				.orElseThrow(() -> new TeamNotFoundException(teamId));
		Profile owner = team.getOwnerProfile();
		if (owner.getStatus() != ProfileStatus.ACTIVE || owner.getUser() == null
				|| !accountAccessService.isAccessAllowed(owner.getUser().getId())
				|| !memberRepository.existsById(new TeamMemberId(teamId, owner.getId()))) {
			throw new TeamNotFoundException(teamId);
		}
		UUID viewerProfileId = profileService.findOptionalIdByUserId(userId).orElse(null);
		if (viewerProfileId != null
				&& blockService.isBlockedBetween(viewerProfileId, owner.getId())) {
			throw new TeamNotFoundException(teamId);
		}
		return team;
	}

	@Transactional(readOnly = true)
	public boolean isOwner(UUID profileId, UUID teamId) {
		return teamRepository.findOwnerProfileId(teamId).filter(profileId::equals).isPresent();
	}

	@Transactional(readOnly = true)
	public Team requireOwnedByUser(UUID userId, UUID teamId) {
		accountAccessService.ensureAccessAllowed(userId);
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new TeamNotFoundException(teamId));
		ensureOwner(team, actorProfileIdForUser(userId));
		return team;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Team requireOwnedForUpdate(UUID userId, UUID teamId) {
		return requireOwnedForUpdate(userId, teamId, List.of());
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Team requireOwnedForUpdate(
			UUID userId, UUID teamId, Collection<UUID> relatedProfileIds
	) {
		UUID ownerProfileId = teamRepository.findOwnerProfileId(teamId)
				.orElseThrow(() -> new TeamNotFoundException(teamId));
		UUID actorProfileId = actorProfileIdForUser(userId);
		if (!ownerProfileId.equals(actorProfileId)) {
			throw new TeamOwnerRequiredException();
		}
		Set<UUID> profileIds = new HashSet<>(relatedProfileIds);
		profileIds.add(actorProfileId);
		lockService.lock(profileIds, userId);
		Team team = teamRepository.findByIdForUpdate(teamId)
				.orElseThrow(() -> new TeamNotFoundException(teamId));
		accountAccessService.ensureAccessAllowed(userId);
		ensureOwner(team, actorProfileId);
		return team;
	}

	public void ensureOwner(Team team, UUID profileId) {
		if (!team.isOwnedBy(profileId)) {
			throw new TeamOwnerRequiredException();
		}
	}

	public void ensureActive(Team team) {
		if (team.getStatus() != TeamStatus.ACTIVE) {
			throw new TeamNotActiveException(team.getId());
		}
	}

	private UUID actorProfileIdForUser(UUID userId) {
		return profileService.findOptionalIdByUserId(userId)
				.orElseThrow(TeamOwnerRequiredException::new);
	}
}
