package ru.itmo.nemat.weezzy.connection.block;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProfileBlockRepository extends JpaRepository<ProfileBlock, ProfileBlockId> {
	List<ProfileBlock> findByBlockerProfileIdOrderByCreatedAtDesc(UUID profileId);

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
