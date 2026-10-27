package ru.itmo.nemat.weezzy.team.recommendation.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
public interface TeamRecommendationEntryRepository
		extends JpaRepository<TeamRecommendationEntry, UUID> {
	List<TeamRecommendationEntry> findBySessionIdAndIdIn(
			UUID sessionId, Collection<UUID> ids
	);

	@Query("""
			SELECT entry FROM TeamRecommendationEntry entry
			WHERE entry.sessionId = :sessionId
			  AND entry.ordinal >= :startInclusive AND entry.ordinal < :endExclusive
			ORDER BY entry.ordinal ASC
			""")
	List<TeamRecommendationEntry> findWindow(
			@Param("sessionId") UUID sessionId, @Param("startInclusive") int startInclusive,
			@Param("endExclusive") int endExclusive
	);

	@Modifying(flushAutomatically = true)
	@Transactional
	@Query("""
			UPDATE TeamRecommendationEntry entry SET entry.firstDeliveredAt = :deliveredAt
			WHERE entry.sessionId = :sessionId AND entry.id IN :entryIds
			  AND entry.firstDeliveredAt IS NULL
			""")
	int markDelivered(
			@Param("sessionId") UUID sessionId, @Param("entryIds") Collection<UUID> entryIds,
			@Param("deliveredAt") LocalDateTime deliveredAt
	);
}
