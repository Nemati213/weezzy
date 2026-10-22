package ru.itmo.nemat.weezzy.team.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamMemberVisibilityService {
	private final AccountAccessService accountAccessService;
	private final ProfileBlockRepository blockRepository;

	@Transactional(readOnly = true)
	public List<TeamMember> filter(List<TeamMember> members, UUID viewerProfileId) {
		Set<UUID> restrictedUserIds = accountAccessService.findRestrictedUserIds(
				members.stream().map(TeamMember::getProfile)
						.filter(profile -> profile.getUser() != null)
						.map(profile -> profile.getUser().getId())
						.collect(Collectors.toSet()));
		Set<UUID> blockedProfileIds = blockedProfileIds(members, viewerProfileId);
		return members.stream()
				.filter(member -> member.getProfile().getStatus() == ProfileStatus.ACTIVE)
				.filter(member -> member.getProfile().getUser() != null)
				.filter(member -> !restrictedUserIds.contains(member.getProfile().getUser().getId()))
				.filter(member -> !blockedProfileIds.contains(member.getProfile().getId()))
				.toList();
	}

	private Set<UUID> blockedProfileIds(List<TeamMember> members, UUID viewerProfileId) {
		if (viewerProfileId == null || members.isEmpty()) {
			return Set.of();
		}
		Set<UUID> profileIds = members.stream().map(member -> member.getProfile().getId())
				.collect(Collectors.toCollection(HashSet::new));
		profileIds.add(viewerProfileId);
		return blockRepository.findAllWithin(profileIds).stream()
				.filter(block -> viewerProfileId.equals(block.getBlockerProfileId())
						|| viewerProfileId.equals(block.getBlockedProfileId()))
				.map(block -> viewerProfileId.equals(block.getBlockerProfileId())
						? block.getBlockedProfileId() : block.getBlockerProfileId())
				.collect(Collectors.toSet());
	}
}
