package ru.itmo.nemat.weezzy.profile.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.user.UserNotFoundException;
import ru.itmo.nemat.weezzy.user.UserService;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountProfileLockService {
	private final ProfileService profileService;
	private final UserService userService;

	@Transactional(propagation = Propagation.MANDATORY)
	public void lock(Collection<UUID> profileIds, UUID actorUserId) {
		Set<UUID> userIds = new HashSet<>();
		profileIds.forEach(profileId ->
				profileService.findOptionalOwnerUserId(profileId).ifPresent(userIds::add));
		if (actorUserId != null) {
			userIds.add(actorUserId);
		}
		boolean actorExists = userService.findAllByIdsForUpdate(userIds).stream()
				.anyMatch(user -> user.getId().equals(actorUserId));
		if (actorUserId != null && !actorExists) {
			throw new UserNotFoundException(actorUserId);
		}
		profileService.findAllByIdsForUpdate(profileIds);
	}
}
