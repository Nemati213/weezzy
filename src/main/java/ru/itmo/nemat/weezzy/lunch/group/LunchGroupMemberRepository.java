package ru.itmo.nemat.weezzy.lunch.group;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LunchGroupMemberRepository
		extends JpaRepository<LunchGroupMember, LunchGroupMemberId> {
	@EntityGraph(attributePaths = {"profile", "profile.user", "lunchRequest"})
	List<LunchGroupMember> findByGroupIdOrderByJoinedAtAsc(UUID groupId);

	long countByGroupId(UUID groupId);

	boolean existsByLunchRequestId(UUID lunchRequestId);

	@Query("""
			SELECT member
			FROM LunchGroupMember member
			JOIN FETCH member.group lunchGroup
			JOIN FETCH lunchGroup.location
			WHERE member.lunchRequest.id IN :requestIds
			""")
	List<LunchGroupMember> findAllByLunchRequestIds(
			@Param("requestIds") Collection<UUID> requestIds
	);

	@Query("""
			SELECT member.group
			FROM LunchGroupMember member
			JOIN FETCH member.group.location
			WHERE member.profile.id = :profileId
			  AND member.group.status = :status
			""")
	Optional<LunchGroup> findGroupByProfileIdAndStatus(
			@Param("profileId") UUID profileId,
			@Param("status") LunchGroupStatus status
	);
}
