package ru.itmo.nemat.weezzy.lunch.chat.cleanup;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ru.itmo.nemat.weezzy.lunch.chat.LunchChatMessage;

import java.time.LocalDateTime;
import java.util.UUID;

public interface LunchChatCleanupRepository
		extends Repository<LunchChatMessage, UUID> {
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			DELETE FROM lunch_group_messages message
			WHERE message.id IN (
				SELECT candidate.id
				FROM lunch_group_messages candidate
				JOIN lunch_groups lunch_group
				  ON lunch_group.id = candidate.group_id
				WHERE (
					lunch_group.status = 'COMPLETED'
					AND lunch_group.completed_at IS NOT NULL
					AND lunch_group.completed_at <= :cutoff
				) OR (
					lunch_group.status = 'CANCELLED'
					AND lunch_group.cancelled_at IS NOT NULL
					AND lunch_group.cancelled_at <= :cutoff
				)
				ORDER BY COALESCE(
					lunch_group.completed_at,
					lunch_group.cancelled_at
				), candidate.created_at, candidate.id
				LIMIT :batchSize
				FOR UPDATE OF candidate SKIP LOCKED
			)
			""", nativeQuery = true)
	int deleteExpiredBatch(
			@Param("cutoff") LocalDateTime cutoff,
			@Param("batchSize") int batchSize
	);
}
