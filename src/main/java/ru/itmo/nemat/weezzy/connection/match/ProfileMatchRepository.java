package ru.itmo.nemat.weezzy.connection.match;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ProfileMatchRepository extends JpaRepository<ProfileMatch, ProfileMatchId> {
	List<ProfileMatch> findByFirstProfileIdOrSecondProfileIdOrderByCreatedAtDesc(
			UUID firstProfileId,
			UUID secondProfileId
	);

	@Query("""
			SELECT profileMatch
			FROM ProfileMatch profileMatch
			WHERE profileMatch.firstProfileId = :profileId
				OR profileMatch.secondProfileId = :profileId
			ORDER BY profileMatch.createdAt DESC,
				profileMatch.firstProfileId DESC,
				profileMatch.secondProfileId DESC
			""")
	List<ProfileMatch> findFirstPage(
			@Param("profileId") UUID profileId,
			Pageable pageable
	);

	@Query("""
			SELECT profileMatch
			FROM ProfileMatch profileMatch
			WHERE (profileMatch.firstProfileId = :profileId
					OR profileMatch.secondProfileId = :profileId)
				AND (
					profileMatch.createdAt < :createdAt
					OR (
						profileMatch.createdAt = :createdAt
						AND (
							profileMatch.firstProfileId < :firstProfileId
							OR (
								profileMatch.firstProfileId = :firstProfileId
								AND profileMatch.secondProfileId < :secondProfileId
							)
						)
					)
				)
			ORDER BY profileMatch.createdAt DESC,
				profileMatch.firstProfileId DESC,
				profileMatch.secondProfileId DESC
			""")
	List<ProfileMatch> findNextPage(
			@Param("profileId") UUID profileId,
			@Param("createdAt") LocalDateTime createdAt,
			@Param("firstProfileId") UUID firstProfileId,
			@Param("secondProfileId") UUID secondProfileId,
			Pageable pageable
	);
}
