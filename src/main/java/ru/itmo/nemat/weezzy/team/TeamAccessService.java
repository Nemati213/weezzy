package ru.itmo.nemat.weezzy.team;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileNotFoundException;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.user.UserNotFoundException;
import ru.itmo.nemat.weezzy.user.UserRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamAccessService {
	private final TeamRepository teamRepository;
	private final ProfileRepository profileRepository;
	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public UUID currentProfileId(UUID userId) {
		return profileRepository.findIdByUserId(userId)
				.orElseThrow(() -> new ProfileNotFoundException(userId));
	}

	@Transactional(readOnly = true)
	public void ensureOwner(UUID userId, UUID ownerProfileId) {
		if (profileRepository.findOwnerUserId(ownerProfileId).filter(userId::equals).isEmpty()) {
			throw new TeamOwnerRequiredException();
		}
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public Team findForUpdate(UUID teamId) {
		return teamRepository.findByIdForUpdate(teamId)
				.orElseThrow(() -> new TeamNotFoundException(teamId));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void lockProfiles(Collection<UUID> profileIds, UUID actorUserId) {
		Set<UUID> userIds = new HashSet<>();
		profileIds.forEach(profileId ->
				profileRepository.findOwnerUserId(profileId).ifPresent(userIds::add));
		if (actorUserId != null) {
			userIds.add(actorUserId);
		}
		if (!userIds.isEmpty()) {
			boolean actorExists = userRepository.findAllByIdForUpdate(userIds).stream()
					.anyMatch(user -> user.getId().equals(actorUserId));
			if (actorUserId != null && !actorExists) {
				throw new UserNotFoundException(actorUserId);
			}
		}
		Set<UUID> lockedIds = profileRepository.findAllByIdForUpdate(profileIds).stream()
				.map(Profile::getId).collect(Collectors.toSet());
		for (UUID profileId : profileIds) {
			if (!lockedIds.contains(profileId)) {
				throw new ProfileNotFoundException(profileId);
			}
		}
	}
}
