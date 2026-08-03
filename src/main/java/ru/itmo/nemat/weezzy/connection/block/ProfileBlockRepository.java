package ru.itmo.nemat.weezzy.connection.block;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ProfileBlockRepository extends JpaRepository<ProfileBlock, ProfileBlockId> {
	List<ProfileBlock> findByBlockerProfileIdOrderByCreatedAtDesc(UUID profileId);

	@Query("""
			SELECT profileBlock
			FROM ProfileBlock profileBlock
			WHERE profileBlock.blockerProfileId = :blockerProfileId
			ORDER BY profileBlock.createdAt DESC, profileBlock.blockedProfileId DESC
			""")
	List<ProfileBlock> findFirstPage(
			@Param("blockerProfileId") UUID blockerProfileId,
			Pageable pageable
	);

	@Query("""
			SELECT profileBlock
			FROM ProfileBlock profileBlock
			WHERE profileBlock.blockerProfileId = :blockerProfileId
				AND (
					profileBlock.createdAt < :createdAt
					OR (
						profileBlock.createdAt = :createdAt
						AND profileBlock.blockedProfileId < :blockedProfileId
					)
				)
			ORDER BY profileBlock.createdAt DESC, profileBlock.blockedProfileId DESC
			""")
	List<ProfileBlock> findNextPage(
			@Param("blockerProfileId") UUID blockerProfileId,
			@Param("createdAt") LocalDateTime createdAt,
			@Param("blockedProfileId") UUID blockedProfileId,
			Pageable pageable
	);

	@Query("""
			SELECT CASE WHEN COUNT(profileBlock) > 0 THEN true ELSE false END
			FROM ProfileBlock profileBlock
			WHERE (profileBlock.blockerProfileId = :firstProfileId
					AND profileBlock.blockedProfileId = :secondProfileId)
				OR (profileBlock.blockerProfileId = :secondProfileId
					AND profileBlock.blockedProfileId = :firstProfileId)
			""")
	boolean existsBetween(
			@Param("firstProfileId") UUID firstProfileId,
			@Param("secondProfileId") UUID secondProfileId
	);
}
