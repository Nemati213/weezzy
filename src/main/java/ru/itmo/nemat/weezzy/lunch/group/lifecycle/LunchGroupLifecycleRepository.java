package ru.itmo.nemat.weezzy.lunch.group.lifecycle;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import ru.itmo.nemat.weezzy.lunch.group.LunchGroup;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LunchGroupLifecycleRepository extends Repository<LunchGroup, UUID> {
	@Query(value = """
			SELECT lunch_group.*
			FROM lunch_groups lunch_group
			WHERE lunch_group.status = 'ACTIVE'
			  AND lunch_group.time_slot <= :completionCutoff
			ORDER BY lunch_group.time_slot, lunch_group.id
			LIMIT :batchSize
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<LunchGroup> findDueForCompletion(
			@Param("completionCutoff") LocalDateTime completionCutoff,
			@Param("batchSize") int batchSize
	);

	@Query(value = """
			SELECT lunch_group.*
			FROM lunch_groups lunch_group
			WHERE lunch_group.status = 'ACTIVE'
			  AND lunch_group.time_slot > :now
			ORDER BY lunch_group.lifecycle_checked_at NULLS FIRST,
			         lunch_group.time_slot,
			         lunch_group.id
			LIMIT :batchSize
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<LunchGroup> findUpcomingForValidation(
			@Param("now") LocalDateTime now,
			@Param("batchSize") int batchSize
	);
}
