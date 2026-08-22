package ru.itmo.nemat.weezzy.lunch.group;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.lunch.group.dto.LunchGroupResponse;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LunchGroupService {
	private final LunchGroupQueryRepository queryRepository;

	@Transactional(readOnly = true)
	public LunchGroupResponse findCurrentForUser(UUID userId) {
		LunchGroup group = queryRepository.findCurrentGroupByUserId(
				userId,
				LunchGroupStatus.ACTIVE
		).orElseThrow(() -> new LunchGroupNotFoundException(userId));
		List<LunchGroupMember> members = queryRepository.findMembers(group.getId());
		return LunchGroupResponse.from(group, members);
	}
}
