package ru.itmo.nemat.weezzy.lunch.group.lifecycle;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlock;
import ru.itmo.nemat.weezzy.connection.block.ProfileBlockRepository;
import ru.itmo.nemat.weezzy.lunch.config.LunchProperties;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroup;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupCancellationReason;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMember;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMemberRepository;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupStatus;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequest;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestRepository;
import ru.itmo.nemat.weezzy.lunch.request.LunchRequestStatus;
import ru.itmo.nemat.weezzy.outbox.OutboxEventService;
import ru.itmo.nemat.weezzy.outbox.payload.LunchGroupCancelledPayload;
import ru.itmo.nemat.weezzy.profile.ProfileStatus;
import ru.itmo.nemat.weezzy.user.AccountAccessService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LunchGroupLifecycleService {
	private final LunchGroupLifecycleRepository lifecycleRepository;
	private final LunchGroupMemberRepository memberRepository;
	private final LunchRequestRepository requestRepository;
	private final ProfileBlockRepository blockRepository;
	private final AccountAccessService accountAccessService;
	private final OutboxEventService outboxEventService;
	private final LunchProperties properties;

	@Transactional
	public List<UUID> cancelInvalidGroups(LocalDateTime now, int batchSize) {
		List<LunchGroup> groups = lifecycleRepository.findUpcomingForValidation(
				now,
				batchSize
		);
		if (groups.isEmpty()) {
			return List.of();
		}
		groups.forEach(group -> group.setLifecycleCheckedAt(now));

		Map<UUID, List<LunchGroupMember>> membersByGroup = memberRepository
				.findCurrentByGroupIds(groupIds(groups))
				.stream()
				.collect(Collectors.groupingBy(member -> member.getGroup().getId()));
		Set<UUID> restrictedUserIds = accountAccessService.findRestrictedUserIds(
				membersByGroup.values().stream()
						.flatMap(Collection::stream)
						.map(member -> member.getProfile().getUser())
						.filter(user -> user != null)
						.map(user -> user.getId())
						.collect(Collectors.toSet())
		);
		Set<UUID> profileIds = membersByGroup.values().stream()
						.flatMap(Collection::stream)
						.map(member -> member.getProfile().getId())
						.collect(Collectors.toSet());
		List<ProfileBlock> blocks = profileIds.isEmpty()
				? List.of()
				: blockRepository.findAllWithin(profileIds);
		List<CancellationDecision> decisions = groups.stream()
				.map(group -> cancellationDecision(
						group,
						membersByGroup.getOrDefault(group.getId(), List.of()),
						restrictedUserIds,
						blocks
				))
				.filter(decision -> decision.reason() != null)
				.toList();
		if (decisions.isEmpty()) {
			return List.of();
		}

		List<UUID> requestIds = decisions.stream()
						.flatMap(decision -> decision.members().stream())
						.map(member -> member.getLunchRequest().getId())
						.sorted()
						.toList();
		Map<UUID, LunchRequest> lockedRequests = requestIds.isEmpty()
				? Map.of()
				: requestRepository.findAllByIdForUpdate(requestIds).stream()
						.collect(Collectors.toMap(
								LunchRequest::getId,
								Function.identity()
						));
		List<UUID> cancelledGroupIds = new ArrayList<>(decisions.size());
		for (CancellationDecision decision : decisions) {
			if (cancel(decision, lockedRequests, now)) {
				cancelledGroupIds.add(decision.group().getId());
			}
		}
		return List.copyOf(cancelledGroupIds);
	}

	@Transactional
	public List<UUID> completeDueGroups(LocalDateTime now, int batchSize) {
		List<LunchGroup> groups = lifecycleRepository.findDueForCompletion(
				now.minus(properties.groupDuration()),
				batchSize
		);
		List<UUID> completedGroupIds = new ArrayList<>(groups.size());
		for (LunchGroup group : groups) {
			if (group.getStatus() == LunchGroupStatus.ACTIVE
					&& !group.getTimeSlot()
					.plus(properties.groupDuration())
					.isAfter(now)) {
				group.setStatus(LunchGroupStatus.COMPLETED);
				group.setCompletedAt(now);
				completedGroupIds.add(group.getId());
			}
		}
		return List.copyOf(completedGroupIds);
	}

	private CancellationDecision cancellationDecision(
			LunchGroup group,
			List<LunchGroupMember> members,
			Set<UUID> restrictedUserIds,
			List<ProfileBlock> blocks
	) {
		Set<UUID> eligibleProfileIds = members.stream()
				.filter(member -> member.getProfile().getStatus() == ProfileStatus.ACTIVE)
				.filter(member -> member.getProfile().getUser() != null)
				.filter(member -> !restrictedUserIds.contains(
						member.getProfile().getUser().getId()
				))
				.map(member -> member.getProfile().getId())
				.collect(Collectors.toSet());
		LunchGroupCancellationReason reason = null;
		if (members.size() < 2) {
			reason = LunchGroupCancellationReason.INSUFFICIENT_MEMBERS;
		} else if (eligibleProfileIds.size() != members.size()) {
			reason = LunchGroupCancellationReason.MEMBER_INELIGIBLE;
		} else if (hasBlockWithin(eligibleProfileIds, blocks)) {
			reason = LunchGroupCancellationReason.MEMBERS_INCOMPATIBLE;
		}
		return new CancellationDecision(
				group,
				members,
				eligibleProfileIds,
				reason
		);
	}

	private boolean hasBlockWithin(
			Set<UUID> profileIds,
			List<ProfileBlock> blocks
	) {
		return blocks.stream().anyMatch(block ->
				profileIds.contains(block.getBlockerProfileId())
						&& profileIds.contains(block.getBlockedProfileId())
		);
	}

	private boolean cancel(
			CancellationDecision decision,
			Map<UUID, LunchRequest> lockedRequests,
			LocalDateTime now
	) {
		LunchGroup group = decision.group();
		if (group.getStatus() != LunchGroupStatus.ACTIVE
				|| !group.getTimeSlot().isAfter(now)) {
			return false;
		}
		group.setStatus(LunchGroupStatus.CANCELLED);
		group.setCancelledAt(now);
		group.setCancellationReason(decision.reason());
		for (LunchGroupMember member : decision.members()) {
			member.setReleasedAt(now);
			LunchRequest request = lockedRequests.get(member.getLunchRequest().getId());
			if (request == null || request.getStatus() != LunchRequestStatus.MATCHED) {
				continue;
			}
			if (decision.eligibleProfileIds().contains(member.getProfile().getId())
					&& request.getTimeSlot().isAfter(now)
					&& request.getTimeSlot().toLocalDate().equals(now.toLocalDate())) {
				request.setStatus(LunchRequestStatus.SEARCHING);
			} else {
				request.setStatus(LunchRequestStatus.EXPIRED);
			}
		}
		outboxEventService.publish(new LunchGroupCancelledPayload(
				group.getId(),
				decision.reason()
		));
		return true;
	}

	private List<UUID> groupIds(List<LunchGroup> groups) {
		return groups.stream().map(LunchGroup::getId).toList();
	}

	private record CancellationDecision(
			LunchGroup group,
			List<LunchGroupMember> members,
			Set<UUID> eligibleProfileIds,
			LunchGroupCancellationReason reason
	) {
		private CancellationDecision {
			members = List.copyOf(members);
			eligibleProfileIds = Set.copyOf(eligibleProfileIds);
		}
	}
}
