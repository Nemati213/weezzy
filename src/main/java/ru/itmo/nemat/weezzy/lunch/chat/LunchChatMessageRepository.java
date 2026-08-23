package ru.itmo.nemat.weezzy.lunch.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LunchChatMessageRepository
		extends JpaRepository<LunchChatMessage, UUID> {
	@EntityGraph(attributePaths = {"group", "senderProfile"})
	Optional<LunchChatMessage> findBySenderProfileIdAndClientMessageId(
			UUID senderProfileId,
			UUID clientMessageId
	);

	@EntityGraph(attributePaths = {"group", "senderProfile"})
	@Query("""
			SELECT message
			FROM LunchChatMessage message
			WHERE message.group.id = :groupId
			ORDER BY message.createdAt DESC, message.id DESC
			""")
	List<LunchChatMessage> findLatest(
			@Param("groupId") UUID groupId,
			Pageable pageable
	);

	@EntityGraph(attributePaths = {"group", "senderProfile"})
	@Query("""
			SELECT message
			FROM LunchChatMessage message
			WHERE message.group.id = :groupId
			  AND (
				message.createdAt < :createdAt
				OR (
					message.createdAt = :createdAt
					AND message.id < :messageId
				)
			  )
			ORDER BY message.createdAt DESC, message.id DESC
			""")
	List<LunchChatMessage> findBefore(
			@Param("groupId") UUID groupId,
			@Param("createdAt") LocalDateTime createdAt,
			@Param("messageId") UUID messageId,
			Pageable pageable
	);

	@EntityGraph(attributePaths = {"group", "senderProfile"})
	@Query("""
			SELECT message
			FROM LunchChatMessage message
			WHERE message.group.id = :groupId
			  AND (
				message.createdAt > :createdAt
				OR (
					message.createdAt = :createdAt
					AND message.id > :messageId
				)
			  )
			ORDER BY message.createdAt, message.id
			""")
	List<LunchChatMessage> findAfter(
			@Param("groupId") UUID groupId,
			@Param("createdAt") LocalDateTime createdAt,
			@Param("messageId") UUID messageId,
			Pageable pageable
	);

	long countByGroupIdAndSenderProfileId(UUID groupId, UUID senderProfileId);
}
