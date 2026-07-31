package ru.itmo.nemat.weezzy.connection;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileNotFoundException;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfilePairLockService {
	private final ProfileRepository profileRepository;


	@Transactional(propagation = Propagation.MANDATORY)
	public void lock(UUID firstProfileId, UUID secondProfileId) {
		List<Profile> lockedProfiles = profileRepository.findAllByIdForUpdate(
				List.of(firstProfileId, secondProfileId)
		);
		Set<UUID> lockedProfileIds = lockedProfiles.stream()
				.map(Profile::getId)
				.collect(Collectors.toSet());

		if (!lockedProfileIds.contains(firstProfileId)) {
			throw new ProfileNotFoundException(firstProfileId);
		}
		if (!lockedProfileIds.contains(secondProfileId)) {
			throw new ProfileNotFoundException(secondProfileId);
		}
	}
}
