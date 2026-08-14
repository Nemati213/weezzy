package ru.itmo.nemat.weezzy.connection.vote;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileVoteRepository extends JpaRepository<ProfileVote, ProfileVoteId> {
	Optional<ProfileVote> findBySourceProfileIdAndTargetProfileId(UUID sourceProfileId, UUID targetProfileId);

	List<ProfileVote> findBySourceProfileId(UUID sourceProfileId);

	@Query("""
			SELECT vote
			FROM ProfileVote vote
			WHERE vote.sourceProfileId = :sourceProfileId
			ORDER BY vote.createdAt DESC, vote.targetProfileId DESC
			""")
	List<ProfileVote> findFirstPage(
			@Param("sourceProfileId") UUID sourceProfileId,
			Pageable pageable
	);

	@Query("""
			SELECT vote
			FROM ProfileVote vote
			WHERE vote.sourceProfileId = :sourceProfileId
				AND (
					vote.createdAt < :createdAt
					OR (
						vote.createdAt = :createdAt
						AND vote.targetProfileId < :targetProfileId
					)
				)
			ORDER BY vote.createdAt DESC, vote.targetProfileId DESC
			""")
	List<ProfileVote> findNextPage(
			@Param("sourceProfileId") UUID sourceProfileId,
			@Param("createdAt") LocalDateTime createdAt,
			@Param("targetProfileId") UUID targetProfileId,
			Pageable pageable
	);
	boolean existsBySourceProfileIdAndTargetProfileIdAndAction(
			UUID sourceProfileId,
			UUID targetProfileId,
			ProfileVoteAction action
	);
}
