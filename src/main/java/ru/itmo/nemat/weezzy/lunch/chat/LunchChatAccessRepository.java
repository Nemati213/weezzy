package ru.itmo.nemat.weezzy.lunch.chat;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMember;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroupMemberId;

import java.util.Optional;
import java.util.UUID;

public interface LunchChatAccessRepository
		extends Repository<LunchGroupMember, LunchGroupMemberId> {
	@Query("""
			SELECT member.group.id
			FROM LunchGroupMember member
			WHERE member.profile.user.id = :userId
			  AND member.releasedAt IS NULL
			  AND member.group.status = 'ACTIVE'
			""")
	Optional<UUID> findActiveGroupIdByUserId(@Param("userId") UUID userId);

	@Query("""
			SELECT member
			FROM LunchGroupMember member
			JOIN FETCH member.group
			JOIN FETCH member.profile
			WHERE member.group.id = :groupId
			  AND member.profile.user.id = :userId
			  AND member.releasedAt IS NULL
			""")
	Optional<LunchGroupMember> findCurrentMembership(
			@Param("groupId") UUID groupId,
			@Param("userId") UUID userId
	);
}
