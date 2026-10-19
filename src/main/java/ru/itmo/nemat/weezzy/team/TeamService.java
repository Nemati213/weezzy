package ru.itmo.nemat.weezzy.team;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockRepository;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.profile.lock.AccountProfileLockService;
import ru.itmo.nemat.weezzy.profile.photo.ProfilePhotoService;
import ru.itmo.nemat.weezzy.profile.photo.dto.ProfilePhotoResponse;
import ru.itmo.nemat.weezzy.team.dto.CreateTeamRequest;
import ru.itmo.nemat.weezzy.team.dto.TeamMemberResponse;
import ru.itmo.nemat.weezzy.team.dto.TeamResponse;
import ru.itmo.nemat.weezzy.team.invitation.TeamInvitationService;
import ru.itmo.nemat.weezzy.team.member.TeamMember;
import ru.itmo.nemat.weezzy.team.member.TeamMemberId;
import ru.itmo.nemat.weezzy.team.member.TeamMemberRepository;
import ru.itmo.nemat.weezzy.team.position.TeamPositionService;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {
	private final TeamRepository teamRepository;
	private final TeamMemberRepository memberRepository;
	private final ProfileService profileService;
	private final AccountAccessService accountAccessService;
	private final ProfileBlockRepository blockRepository;
	private final ProfilePhotoService photoService;
	private final TeamAccessService accessService;
	private final AccountProfileLockService lockService;
	private final TeamInvitationService invitationService;
	private final TeamPositionService positionService;

	@Transactional
	public TeamResponse createForUser(UUID userId, CreateTeamRequest request) {
		UUID ownerId = profileService.findIdByUserId(userId);
		lockService.lock(List.of(ownerId), userId);
		Profile owner = profileService.findById(ownerId);
		accountAccessService.ensureAccessAllowed(userId);
		if (owner.getStatus() != ProfileStatus.ACTIVE) {
			throw new TeamProfileNotActiveException(owner.getId());
		}

		Team team = new Team();
		team.setOwnerProfile(owner);
		team.setName(request.name().strip());
		team.setPurpose(request.purpose().strip());
		team.setDescription(normalizeDescription(request.description()));
		teamRepository.save(team);

		TeamMember member = new TeamMember();
		member.setId(new TeamMemberId(team.getId(), owner.getId()));
		member.setTeam(team);
		member.setProfile(owner);
		member.setPosition(positionService.createFounderPosition(team, request.ownerPositionName()));
		member = memberRepository.saveAndFlush(member);
		return toResponse(team, List.of(member));
	}

	@Transactional(readOnly = true)
	public TeamResponse findByIdForUser(UUID userId, UUID teamId) {
		Team team = accessService.requireVisibleForUser(userId, teamId);
		UUID viewerProfileId = profileService.findOptionalByUserId(userId)
				.map(Profile::getId)
				.orElse(null);
		List<TeamMember> members = visibleMembers(
				memberRepository.findByTeamIdOrderByJoinedAtAscProfileIdAsc(teamId),
				viewerProfileId
		);
		if (members.stream().noneMatch(member -> member.getProfile().getId()
				.equals(team.getOwnerProfile().getId()))) {
			throw new TeamNotFoundException(teamId);
		}
		return toResponse(team, members);
	}

	@Transactional
	public void leaveForUser(UUID userId, UUID teamId) {
		UUID ownerId = ownerProfileId(teamId);
		UUID profileId = profileService.findIdByUserId(userId);
		lockService.lock(List.of(ownerId, profileId), userId);
		Team team = teamRepository.findByIdForUpdate(teamId)
				.orElseThrow(() -> new TeamNotFoundException(teamId));
		accountAccessService.ensureAccessAllowed(userId);
		accessService.ensureActive(team);
		ensureNotOwner(team, profileId);
		if (removeMembership(teamId, profileId)) {
			invitationService.cancelPending(teamId, profileId);
		}
	}

	@Transactional
	public void removeMemberForUser(UUID userId, UUID teamId, UUID profileId) {
		Team team = accessService.requireOwnedForUpdate(userId, teamId, List.of(profileId));
		accessService.ensureActive(team);
		ensureNotOwner(team, profileId);
		removeMembership(teamId, profileId);
		invitationService.cancelPending(teamId, profileId);
	}

	@Transactional
	public void archiveForUser(UUID userId, UUID teamId) {
		Team team = accessService.requireOwnedForUpdate(userId, teamId);
		if (team.getStatus() == TeamStatus.ARCHIVED) {
			return;
		}
		team.setStatus(TeamStatus.ARCHIVED);
		invitationService.cancelPending(teamId, null);
	}

	private UUID ownerProfileId(UUID teamId) {
		return teamRepository.findOwnerProfileId(teamId)
				.orElseThrow(() -> new TeamNotFoundException(teamId));
	}

	private void ensureNotOwner(Team team, UUID profileId) {
		if (team.isOwnedBy(profileId)) {
			throw new TeamOwnerCannotLeaveException();
		}
	}

	private boolean removeMembership(UUID teamId, UUID profileId) {
		TeamMember member = memberRepository.findById(new TeamMemberId(teamId, profileId))
				.orElse(null);
		if (member == null) {
			return false;
		}
		memberRepository.delete(member);
		return true;
	}

	private List<TeamMember> visibleMembers(
			List<TeamMember> members,
			UUID viewerProfileId
	) {
		Set<UUID> restrictedUserIds = accountAccessService.findRestrictedUserIds(
				members.stream().map(TeamMember::getProfile)
						.filter(profile -> profile.getUser() != null)
						.map(profile -> profile.getUser().getId())
						.collect(Collectors.toSet())
		);
		Set<UUID> blockedProfileIds = blockedProfileIds(members, viewerProfileId);
		return members.stream()
				.filter(member -> member.getProfile().getStatus() == ProfileStatus.ACTIVE)
				.filter(member -> member.getProfile().getUser() != null)
				.filter(member -> !restrictedUserIds.contains(
						member.getProfile().getUser().getId()
				))
				.filter(member -> !blockedProfileIds.contains(member.getProfile().getId()))
				.toList();
	}

	private Set<UUID> blockedProfileIds(List<TeamMember> members, UUID viewerProfileId) {
		if (viewerProfileId == null) {
			return Set.of();
		}
		Set<UUID> profileIds = members.stream()
				.map(member -> member.getProfile().getId())
				.collect(Collectors.toCollection(HashSet::new));
		profileIds.add(viewerProfileId);
		return blockRepository.findAllWithin(profileIds).stream()
				.filter(block -> viewerProfileId.equals(block.getBlockerProfileId())
						|| viewerProfileId.equals(block.getBlockedProfileId()))
				.map(block -> viewerProfileId.equals(block.getBlockerProfileId())
						? block.getBlockedProfileId() : block.getBlockerProfileId())
				.collect(Collectors.toSet());
	}

	private TeamResponse toResponse(Team team, List<TeamMember> members) {
		Map<UUID, List<ProfilePhotoResponse>> photosByProfileId =
				photoService.findReadyPhotosByProfileIds(
						members.stream().map(member -> member.getProfile().getId()).toList()
				);
		return TeamResponse.from(team, members.stream()
				.map(member -> TeamMemberResponse.from(member, photosByProfileId.getOrDefault(
						member.getProfile().getId(), List.of()
				)))
				.toList(), positionService.findResponsesByTeam(team.getId()));
	}

	private String normalizeDescription(String description) {
		return description == null || description.isBlank() ? null : description.strip();
	}
}
