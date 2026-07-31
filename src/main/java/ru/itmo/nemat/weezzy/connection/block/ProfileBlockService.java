package ru.itmo.nemat.weezzy.connection.block;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.ProfilePairLockService;
import ru.itmo.nemat.weezzy.connection.block.dto.ProfileBlockResponse;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchId;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchRepository;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileBlockService {
	private final ProfileBlockRepository repository;
	private final ProfileService profileService;
	private final ProfileMatchRepository matchRepository;
	private final ProfilePairLockService pairLockService;

	@Transactional
	public ProfileBlockResponse block(UUID blockerProfileId, UUID blockedProfileId) {
		if (blockerProfileId.equals(blockedProfileId)) {
			throw new SelfBlockException(blockerProfileId);
		}
		pairLockService.lock(blockerProfileId, blockedProfileId);
		Profile blockedProfile = profileService.findById(blockedProfileId);

		ProfileBlockId blockId = new ProfileBlockId(blockerProfileId, blockedProfileId);
		ProfileBlock profileBlock = repository.findById(blockId)
				.orElseGet(() -> saveNewBlock(blockId));
		deleteMatchIfExists(blockerProfileId, blockedProfileId);

		return ProfileBlockResponse.from(profileBlock, blockedProfile);
	}

	@Transactional(readOnly = true)
	public List<ProfileBlockResponse> findBlocksByProfileId(UUID blockerProfileId) {
		profileService.findById(blockerProfileId);

		List<ProfileBlock> blocks =
				repository.findByBlockerProfileIdOrderByCreatedAtDesc(blockerProfileId);

		Set<UUID> blockedProfileIds = blocks.stream()
				.map(ProfileBlock::getBlockedProfileId)
				.collect(Collectors.toSet());

		Map<UUID, Profile> profilesById = profileService.findAllByIds(blockedProfileIds)
				.stream()
				.collect(Collectors.toMap(Profile::getId, profile -> profile));

		return blocks.stream()
				.map(profileBlock -> ProfileBlockResponse.from(
						profileBlock,
						profilesById.get(profileBlock.getBlockedProfileId())
				))
				.toList();
	}

	@Transactional
	public void unblock(UUID blockerProfileId, UUID blockedProfileId) {
		if (blockerProfileId.equals(blockedProfileId)) {
			return;
		}
		pairLockService.lock(blockerProfileId, blockedProfileId);
		ProfileBlockId blockId = new ProfileBlockId(blockerProfileId, blockedProfileId);
		if (repository.existsById(blockId)) {
			repository.deleteById(blockId);
		}
	}

	@Transactional(readOnly = true)
	public boolean isBlockedBetween(UUID firstProfileId, UUID secondProfileId) {
		return repository.existsBetween(firstProfileId, secondProfileId);
	}

	@Transactional(readOnly = true)
	public void ensureInteractionAllowed(UUID firstProfileId, UUID secondProfileId) {
		if (isBlockedBetween(firstProfileId, secondProfileId)) {
			throw new ProfileInteractionBlockedException(firstProfileId, secondProfileId);
		}
	}

	private void deleteMatchIfExists(UUID firstProfileId, UUID secondProfileId) {
		ProfileMatchId matchId = normalizedMatchId(firstProfileId, secondProfileId);
		if (matchRepository.existsById(matchId)) {
			matchRepository.deleteById(matchId);
		}
	}

	private ProfileMatchId normalizedMatchId(UUID firstProfileId, UUID secondProfileId) {
		if (firstProfileId.toString().compareTo(secondProfileId.toString()) < 0) {
			return new ProfileMatchId(firstProfileId, secondProfileId);
		}

		return new ProfileMatchId(secondProfileId, firstProfileId);
	}

	private ProfileBlock saveNewBlock(ProfileBlockId blockId) {
		ProfileBlock profileBlock = new ProfileBlock();
		profileBlock.setBlockerProfileId(blockId.getBlockerProfileId());
		profileBlock.setBlockedProfileId(blockId.getBlockedProfileId());

		return repository.saveAndFlush(profileBlock);
	}
}
