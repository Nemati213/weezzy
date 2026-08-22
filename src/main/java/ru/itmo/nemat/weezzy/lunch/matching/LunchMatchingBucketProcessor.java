package ru.itmo.nemat.weezzy.lunch.matching;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockRepository;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupFormationService;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMemberRepository;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupStatus;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.user.AccountAccessService;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LunchMatchingBucketProcessor {
	private final LunchMatchingRepository matchingRepository;
	private final UserRepository userRepository;
	private final ProfileRepository profileRepository;
	private final ProfileBlockRepository blockRepository;
	private final LunchGroupMemberRepository memberRepository;
	private final AccountAccessService accountAccessService;
	private final LunchMatchingPipeline matchingPipeline;
	private final LunchGroupFormationService formationService;

	@Transactional
	public LunchMatchingBucketProcessingResult process(
			MatchingBucketKey bucketKey,
			LocalDateTime now
	) {
		if (!matchingRepository.tryClaimBucket(bucketKey.advisoryLockKey())) {
			return LunchMatchingBucketProcessingResult.notClaimed();
		}
		if (!bucketKey.timeSlot().isAfter(now)) {
			return new LunchMatchingBucketProcessingResult(true, 0, 0, 0);
		}

		List<UUID> userIds = matchingRepository.findOwnerUserIds(
				bucketKey.locationId(),
				bucketKey.timeSlot()
		).stream().distinct().sorted().toList();
		List<UUID> profileIds = matchingRepository.findProfileIds(
				bucketKey.locationId(),
				bucketKey.timeSlot()
		).stream().distinct().sorted().toList();

		List<User> lockedUsers = userIds.isEmpty()
				? List.of()
				: userRepository.findAllByIdForUpdate(userIds);
		List<Profile> lockedProfiles = profileIds.isEmpty()
				? List.of()
				: profileRepository.findAllByIdForUpdate(profileIds);
		List<LunchRequest> lockedRequests = matchingRepository.findRequestsForUpdate(
				bucketKey.locationId(),
				bucketKey.timeSlot()
		);
		if (lockedRequests.isEmpty()) {
			return new LunchMatchingBucketProcessingResult(true, 0, 0, 0);
		}

		Set<UUID> lockedUserIds = lockedUsers.stream()
				.map(User::getId)
				.collect(Collectors.toSet());
		Map<UUID, Profile> profilesById = lockedProfiles.stream()
				.collect(Collectors.toMap(Profile::getId, Function.identity()));
		Set<UUID> restrictedUserIds = accountAccessService.findRestrictedUserIds(
				lockedUserIds
		);
		Set<UUID> activeGroupProfileIds = profilesById.isEmpty()
				? Set.of()
				: memberRepository.findProfileIdsByGroupStatus(
						profilesById.keySet(),
						LunchGroupStatus.ACTIVE
				);

		List<MatchingCandidate> candidates = lockedRequests.stream()
				.filter(request -> isEligible(
						profilesById.get(request.getProfile().getId()),
						lockedUserIds,
						restrictedUserIds,
						activeGroupProfileIds
				))
				.map(this::toCandidate)
				.toList();
		Set<MatchingProfilePair> incompatiblePairs = findIncompatiblePairs(candidates);
		MatchingPipelineResult matchingResult = matchingPipeline.match(
				new MatchingBucket(
						bucketKey.locationId(),
						bucketKey.timeSlot(),
						candidates
				),
				now,
				incompatiblePairs
		);

		matchingResult.groups().forEach(group -> formationService.formGroup(
				group.requestIds(),
				group.topic()
		));
		int matchedCandidateCount = matchingResult.groups().stream()
				.mapToInt(group -> group.candidates().size())
				.sum();
		return new LunchMatchingBucketProcessingResult(
				true,
				matchingResult.groups().size(),
				matchedCandidateCount,
				lockedRequests.size() - matchedCandidateCount
		);
	}

	private boolean isEligible(
			Profile profile,
			Set<UUID> lockedUserIds,
			Set<UUID> restrictedUserIds,
			Set<UUID> activeGroupProfileIds
	) {
		if (profile == null
				|| profile.getStatus() != ProfileStatus.ACTIVE
				|| profile.getUser() == null) {
			return false;
		}
		UUID userId = profile.getUser().getId();
		return lockedUserIds.contains(userId)
				&& !restrictedUserIds.contains(userId)
				&& !activeGroupProfileIds.contains(profile.getId());
	}

	private MatchingCandidate toCandidate(LunchRequest request) {
		return new MatchingCandidate(
				request.getId(),
				request.getProfile().getId(),
				request.getTopic(),
				request.getCreatedAt()
		);
	}

	private Set<MatchingProfilePair> findIncompatiblePairs(
			List<MatchingCandidate> candidates
	) {
		Set<UUID> profileIds = candidates.stream()
				.map(MatchingCandidate::profileId)
				.collect(Collectors.toSet());
		if (profileIds.size() < 2) {
			return Set.of();
		}
		return blockRepository.findAllWithin(profileIds).stream()
				.map(block -> new MatchingProfilePair(
						block.getBlockerProfileId(),
						block.getBlockedProfileId()
				))
				.collect(Collectors.toSet());
	}
}
