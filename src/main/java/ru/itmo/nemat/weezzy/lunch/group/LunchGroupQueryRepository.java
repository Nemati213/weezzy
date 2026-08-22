package ru.itmo.nemat.weezzy.lunch.group;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LunchGroupQueryRepository
		extends Repository<LunchGroupMember, LunchGroupMemberId> {
	@Query("""
			SELECT member.group
			FROM LunchGroupMember member
			JOIN FETCH member.group.location location
			JOIN FETCH location.university
			WHERE member.profile.user.id = :userId
			  AND member.group.status = :status
			""")
	Optional<LunchGroup> findCurrentGroupByUserId(
			@Param("userId") UUID userId,
			@Param("status") LunchGroupStatus status
	);

	@EntityGraph(attributePaths = "profile")
	@Query("""
			SELECT member
			FROM LunchGroupMember member
			WHERE member.group.id = :groupId
			ORDER BY member.joinedAt, member.profile.id
			""")
	List<LunchGroupMember> findMembers(@Param("groupId") UUID groupId);
}
