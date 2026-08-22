package ru.itmo.nemat.weezzy.lunch.group;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface LunchGroupMemberRepository
		extends JpaRepository<LunchGroupMember, LunchGroupMemberId> {
	@EntityGraph(attributePaths = {"profile", "profile.user", "lunchRequest"})
	@Query("""
			SELECT member
			FROM LunchGroupMember member
			WHERE member.group.id = :groupId
			ORDER BY member.joinedAt, member.profile.id
			""")
	List<LunchGroupMember> findByGroupIdOrderByJoinedAtAsc(
			@Param("groupId") UUID groupId
	);

	long countByGroupId(UUID groupId);

	boolean existsByLunchRequestIdAndReleasedAtIsNull(UUID lunchRequestId);

	@Query("""
			SELECT member
			FROM LunchGroupMember member
			JOIN FETCH member.group lunchGroup
			JOIN FETCH lunchGroup.location
			WHERE member.lunchRequest.id IN :requestIds
			  AND member.releasedAt IS NULL
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
			  AND member.releasedAt IS NULL
			""")
	Optional<LunchGroup> findGroupByProfileIdAndStatus(
			@Param("profileId") UUID profileId,
			@Param("status") LunchGroupStatus status
	);

	@Query("""
			SELECT DISTINCT member.profile.id
			FROM LunchGroupMember member
			WHERE member.profile.id IN :profileIds
			  AND member.group.status = :status
			  AND member.releasedAt IS NULL
			""")
	Set<UUID> findProfileIdsByGroupStatus(
			@Param("profileIds") Collection<UUID> profileIds,
			@Param("status") LunchGroupStatus status
	);

	@EntityGraph(attributePaths = {"profile", "profile.user", "lunchRequest"})
	@Query("""
			SELECT member
			FROM LunchGroupMember member
			WHERE member.group.id IN :groupIds
			  AND member.releasedAt IS NULL
			ORDER BY member.group.id, member.profile.id
			""")
	List<LunchGroupMember> findCurrentByGroupIds(
			@Param("groupIds") Collection<UUID> groupIds
	);
}
