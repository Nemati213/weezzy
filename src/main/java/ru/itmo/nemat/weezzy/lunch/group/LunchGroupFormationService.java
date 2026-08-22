package ru.itmo.nemat.weezzy.lunch.group;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockRepository;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestRepository;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestStatus;
import ru.itmo.nemat.weezzy.lunch.request.LunchTopic;
import ru.itmo.nemat.weezzy.outbox.OutboxEventService;
import ru.itmo.nemat.weezzy.outbox.payload.LunchGroupFormedPayload;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileRepository;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.user.AccountAccessService;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LunchGroupFormationService {
	private static final int MIN_GROUP_SIZE = 2;
	private static final int MAX_GROUP_SIZE = 4;

	private final LunchRequestRepository requestRepository;
	private final LunchGroupRepository groupRepository;
	private final LunchGroupMemberRepository memberRepository;
	private final UserRepository userRepository;
	private final ProfileRepository profileRepository;
	private final ProfileBlockRepository blockRepository;
	private final AccountAccessService accountAccessService;
	private final OutboxEventService outboxEventService;

	@Transactional
	public LunchGroup formGroup(
			Collection<UUID> requestIds,
			LunchTopic groupTopic
	) {
		List<UUID> normalizedRequestIds = normalizeCandidateIds(requestIds);
		if (groupTopic == null) {
			throw new InvalidLunchGroupCandidatesException("group topic is required");
		}

		List<UUID> candidateProfileIds = requestRepository.findProfileIdsByRequestIds(
				normalizedRequestIds
		);
		if (candidateProfileIds.size() != normalizedRequestIds.size()) {
			throw new LunchGroupFormationConflictException(
					"one or more lunch requests no longer exist"
			);
		}

		List<UUID> profileIds = candidateProfileIds.stream()
				.distinct()
				.sorted()
				.toList();
		if (profileIds.size() != normalizedRequestIds.size()) {
			throw new LunchGroupFormationConflictException(
					"a profile cannot participate more than once"
			);
		}

		List<UUID> candidateUserIds = requestRepository.findOwnerUserIdsByRequestIds(
				normalizedRequestIds
		);
		if (candidateUserIds.size() != normalizedRequestIds.size()) {
			throw new LunchGroupFormationConflictException(
					"one or more profiles no longer have an owner"
			);
		}

		List<UUID> userIds = candidateUserIds.stream()
				.distinct()
				.sorted()
				.toList();
		if (userIds.size() != normalizedRequestIds.size()) {
			throw new LunchGroupFormationConflictException(
					"a user cannot participate more than once"
			);
		}

		List<User> lockedUsers = userRepository.findAllByIdForUpdate(userIds);
		if (lockedUsers.size() != userIds.size()) {
			throw new LunchGroupFormationConflictException(
					"one or more users no longer exist"
			);
		}

		List<Profile> lockedProfiles = profileRepository.findAllByIdForUpdate(
				profileIds
		);
		if (lockedProfiles.size() != profileIds.size()) {
			throw new LunchGroupFormationConflictException(
					"one or more profiles no longer exist"
			);
		}

		List<LunchRequest> lockedRequests = requestRepository.findAllByIdForUpdate(
				normalizedRequestIds
		);
		ensureAllRequestsFound(lockedRequests, normalizedRequestIds);

		if (lockedRequests.stream()
				.allMatch(request -> request.getStatus() == LunchRequestStatus.MATCHED)) {
			return resolveIdempotentResult(lockedRequests, groupTopic);
		}

		ensureAllSearching(lockedRequests);
		ensureProfilesEligible(lockedProfiles);
		ensureNoEffectiveSanctions(userIds);
		ensureSameBucket(lockedRequests);
		ensureNoBlocks(profileIds);

		LunchRequest firstRequest = lockedRequests.getFirst();
		LunchGroup group = new LunchGroup();
		group.setLocation(firstRequest.getLocation());
		group.setTimeSlot(firstRequest.getTimeSlot());
		group.setTopic(groupTopic);
		LunchGroup savedGroup = groupRepository.saveAndFlush(group);

		List<LunchGroupMember> members = lockedRequests.stream()
				.map(request -> createMember(savedGroup, request))
				.toList();
		lockedRequests.forEach(request -> request.setStatus(LunchRequestStatus.MATCHED));
		memberRepository.saveAllAndFlush(members);
		outboxEventService.publish(new LunchGroupFormedPayload(savedGroup.getId()));
		return savedGroup;
	}

	private List<UUID> normalizeCandidateIds(Collection<UUID> requestIds) {
		if (requestIds == null
				|| requestIds.size() < MIN_GROUP_SIZE
				|| requestIds.size() > MAX_GROUP_SIZE) {
			throw new InvalidLunchGroupCandidatesException(
					"group size must be between 2 and 4"
			);
		}
		if (requestIds.stream().anyMatch(id -> id == null)) {
			throw new InvalidLunchGroupCandidatesException(
					"request IDs must not be null"
			);
		}

		Set<UUID> uniqueIds = new HashSet<>(requestIds);
		if (uniqueIds.size() != requestIds.size()) {
			throw new InvalidLunchGroupCandidatesException(
					"request IDs must be unique"
			);
		}
		return uniqueIds.stream().sorted().toList();
	}

	private void ensureAllRequestsFound(
			List<LunchRequest> requests,
			List<UUID> expectedIds
	) {
		Set<UUID> foundIds = requests.stream()
				.map(LunchRequest::getId)
				.collect(Collectors.toSet());
		if (foundIds.size() != expectedIds.size()
				|| !foundIds.containsAll(expectedIds)) {
			throw new LunchGroupFormationConflictException(
					"one or more lunch requests no longer exist"
			);
		}
	}

	private void ensureAllSearching(List<LunchRequest> requests) {
		requests.stream()
				.filter(request -> request.getStatus() != LunchRequestStatus.SEARCHING)
				.findFirst()
				.ifPresent(request -> {
					throw new LunchGroupFormationConflictException(
							"request %s has status %s".formatted(
									request.getId(),
									request.getStatus()
							)
					);
				});
	}

	private void ensureProfilesEligible(List<Profile> profiles) {
		profiles.stream()
				.filter(profile -> profile.getStatus() != ProfileStatus.ACTIVE
						|| profile.getUser() == null)
				.findFirst()
				.ifPresent(profile -> {
					throw new LunchGroupFormationConflictException(
							"profile is not eligible: " + profile.getId()
					);
				});
	}

	private void ensureSameBucket(List<LunchRequest> requests) {
		LunchRequest first = requests.getFirst();
		boolean sameBucket = requests.stream().allMatch(request ->
				request.getLocation().getId().equals(first.getLocation().getId())
						&& request.getTimeSlot().equals(first.getTimeSlot())
		);
		if (!sameBucket) {
			throw new LunchGroupFormationConflictException(
					"requests must have the same location and time slot"
			);
		}
	}

	private void ensureNoBlocks(List<UUID> profileIds) {
		if (blockRepository.existsWithin(profileIds)) {
			throw new LunchGroupFormationConflictException(
					"one or more candidate profiles are blocked"
			);
		}
	}

	private void ensureNoEffectiveSanctions(List<UUID> userIds) {
		if (!accountAccessService.findRestrictedUserIds(userIds).isEmpty()) {
			throw new LunchGroupFormationConflictException(
					"one or more candidate users have an active sanction"
			);
		}
	}

	private LunchGroup resolveIdempotentResult(
			List<LunchRequest> requests,
			LunchTopic groupTopic
	) {
		List<UUID> requestIds = requests.stream().map(LunchRequest::getId).toList();
		List<LunchGroupMember> existingMembers = memberRepository
				.findAllByLunchRequestIds(requestIds);
		Set<UUID> groupIds = existingMembers.stream()
				.map(member -> member.getGroup().getId())
				.collect(Collectors.toSet());

		if (existingMembers.size() != requests.size() || groupIds.size() != 1) {
			throw new LunchGroupFormationConflictException(
					"requests were already used by another formation"
			);
		}

		LunchGroup existingGroup = existingMembers.getFirst().getGroup();
		if (memberRepository.countByGroupId(existingGroup.getId()) != requests.size()
				|| existingGroup.getTopic() != groupTopic) {
			throw new LunchGroupFormationConflictException(
					"requests do not identify the same previously formed group"
			);
		}
		return existingGroup;
	}

	private LunchGroupMember createMember(
			LunchGroup group,
			LunchRequest request
	) {
		LunchGroupMember member = new LunchGroupMember();
		member.setId(new LunchGroupMemberId(
				group.getId(),
				request.getProfile().getId()
		));
		member.setGroup(group);
		member.setProfile(request.getProfile());
		member.setLunchRequest(request);
		return member;
	}
}
