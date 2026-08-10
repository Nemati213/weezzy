package ru.itmo.nemat.weezzy.connection.block;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.common.dto.CursorPageResponse;
import ru.itmo.nemat.weezzy.connection.ProfilePairLockService;
import ru.itmo.nemat.weezzy.connection.event.ProfileInteractionEventService;
import ru.itmo.nemat.weezzy.connection.event.ProfileInteractionEventType;
import ru.itmo.nemat.weezzy.connection.block.dto.ProfileBlockResponse;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchId;
import ru.itmo.nemat.weezzy.connection.match.ProfileMatchRepository;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.photo.ProfilePhotoService;
import ru.itmo.nemat.weezzy.profile.photo.dto.ProfilePhotoResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
	private final BlockCursorCodec cursorCodec;
	private final ProfileInteractionEventService interactionEventService;
	private final ProfilePhotoService profilePhotoService;

	@Transactional
	public ProfileBlockResponse block(UUID blockerProfileId, UUID blockedProfileId) {
		if (blockerProfileId.equals(blockedProfileId)) {
			throw new SelfBlockException(blockerProfileId);
		}
		pairLockService.lock(blockerProfileId, blockedProfileId);
		profileService.ensureActive(blockerProfileId);
		profileService.ensureActive(blockedProfileId);
		Profile blockedProfile = profileService.findById(blockedProfileId);

		ProfileBlockId blockId = new ProfileBlockId(blockerProfileId, blockedProfileId);
		Optional<ProfileBlock> existingBlock = repository.findById(blockId);
		ProfileBlock profileBlock = existingBlock.orElseGet(() -> saveNewBlock(blockId));
		if (existingBlock.isEmpty()) {
			interactionEventService.record(
					blockerProfileId,
					blockedProfileId,
					ProfileInteractionEventType.BLOCK
			);
		}
		deleteMatchIfExists(blockerProfileId, blockedProfileId);

		return ProfileBlockResponse.from(
				profileBlock,
				blockedProfile,
				profilePhotoService.findReadyPhotos(blockedProfileId)
		);
	}

	@Transactional(readOnly = true)
	public List<ProfileBlockResponse> findBlocksByProfileId(UUID blockerProfileId) {
		profileService.findById(blockerProfileId);

		List<ProfileBlock> blocks =
				repository.findByBlockerProfileIdOrderByCreatedAtDesc(blockerProfileId);

		return toResponses(blocks);
	}

	@Transactional(readOnly = true)
	public CursorPageResponse<ProfileBlockResponse> findBlocksPageByProfileId(
			UUID blockerProfileId,
			String encodedCursor,
			int limit
	) {
		profileService.findById(blockerProfileId);
		BlockCursor cursor = cursorCodec.decode(encodedCursor);
		PageRequest pageRequest = PageRequest.of(0, limit + 1);

		List<ProfileBlock> fetched = cursor == null
				? repository.findFirstPage(blockerProfileId, pageRequest)
				: repository.findNextPage(
						blockerProfileId,
						cursor.createdAt(),
						cursor.blockedProfileId(),
						pageRequest
				);
		boolean hasNext = fetched.size() > limit;
		List<ProfileBlock> page = fetched.stream().limit(limit).toList();
		List<ProfileBlockResponse> content = toResponses(page);
		String nextCursor = hasNext
				? cursorCodec.encode(toCursor(page.getLast()))
				: null;

		return new CursorPageResponse<>(content, nextCursor);
	}

	@Transactional
	public void unblock(UUID blockerProfileId, UUID blockedProfileId) {
		if (blockerProfileId.equals(blockedProfileId)) {
			return;
		}
		pairLockService.lock(blockerProfileId, blockedProfileId);
		profileService.ensureActive(blockerProfileId);
		profileService.ensureActive(blockedProfileId);
		ProfileBlockId blockId = new ProfileBlockId(blockerProfileId, blockedProfileId);
		if (repository.existsById(blockId)) {
			repository.deleteById(blockId);
			interactionEventService.record(
					blockerProfileId,
					blockedProfileId,
					ProfileInteractionEventType.UNBLOCK
			);
		}
	}

	@Transactional(readOnly = true)
	public boolean isBlockedBetween(UUID firstProfileId, UUID secondProfileId) {
		return repository.existsBetween(firstProfileId, secondProfileId);
	}

	@Transactional(readOnly = true)
	public void ensureInteractionAllowed(UUID firstProfileId, UUID secondProfileId) {
		profileService.ensureActive(firstProfileId);
		profileService.ensureActive(secondProfileId);
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

	private List<ProfileBlockResponse> toResponses(List<ProfileBlock> blocks) {
		Set<UUID> blockedProfileIds = blocks.stream()
				.map(ProfileBlock::getBlockedProfileId)
				.collect(Collectors.toSet());
		Map<UUID, Profile> profilesById = profileService.findAllByIds(blockedProfileIds)
				.stream()
				.collect(Collectors.toMap(Profile::getId, profile -> profile));
		Map<UUID, List<ProfilePhotoResponse>> photosByProfileId =
				profilePhotoService.findReadyPhotosByProfileIds(blockedProfileIds);

		return blocks.stream()
				.map(profileBlock -> ProfileBlockResponse.from(
						profileBlock,
						profilesById.get(profileBlock.getBlockedProfileId()),
						photosByProfileId.getOrDefault(
								profileBlock.getBlockedProfileId(),
								List.of()
						)
				))
				.toList();
	}

	private BlockCursor toCursor(ProfileBlock profileBlock) {
		return new BlockCursor(
				profileBlock.getCreatedAt(),
				profileBlock.getBlockedProfileId()
		);
	}
}
