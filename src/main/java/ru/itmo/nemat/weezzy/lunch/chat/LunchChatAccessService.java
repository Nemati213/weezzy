package ru.itmo.nemat.weezzy.lunch.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroup;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMember;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupNotFoundException;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupRepository;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupStatus;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LunchChatAccessService {
	private final LunchChatAccessRepository accessRepository;
	private final LunchGroupRepository groupRepository;

	@Transactional(propagation = Propagation.MANDATORY)
	public LunchGroupMember requireActiveMembershipForUpdate(UUID userId) {
		UUID groupId = findActiveGroupId(userId);
		LunchGroup group = groupRepository.findByIdForUpdate(groupId)
				.orElseThrow(LunchGroupNotFoundException::new);
		return revalidateMembership(group, userId);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public LunchGroupMember requireActiveMembershipForRead(UUID userId) {
		UUID groupId = findActiveGroupId(userId);
		LunchGroup group = groupRepository.findById(groupId)
				.orElseThrow(LunchGroupNotFoundException::new);
		return revalidateMembership(group, userId);
	}

	private UUID findActiveGroupId(UUID userId) {
		return accessRepository.findActiveGroupIdByUserId(userId)
				.orElseThrow(LunchGroupNotFoundException::new);
	}

	private LunchGroupMember revalidateMembership(
			LunchGroup group,
			UUID userId
	) {
		if (group.getStatus() != LunchGroupStatus.ACTIVE) {
			throw new LunchGroupNotFoundException();
		}
		return accessRepository.findCurrentMembership(group.getId(), userId)
				.orElseThrow(LunchGroupNotFoundException::new);
	}
}
