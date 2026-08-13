package ru.itmo.nemat.weezzy.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
	@Query("""
			SELECT notification
			FROM Notification notification
			WHERE notification.recipientUserId = :userId
			ORDER BY notification.createdAt DESC, notification.id DESC
			""")
	List<Notification> findFirstPage(
			@Param("userId") UUID userId,
			Pageable pageable
	);

	@Query("""
			SELECT notification
			FROM Notification notification
			WHERE notification.recipientUserId = :userId
				AND (
					notification.createdAt < :createdAt
					OR (
						notification.createdAt = :createdAt
						AND notification.id < :notificationId
					)
				)
			ORDER BY notification.createdAt DESC, notification.id DESC
			""")
	List<Notification> findNextPage(
			@Param("userId") UUID userId,
			@Param("createdAt") LocalDateTime createdAt,
			@Param("notificationId") UUID notificationId,
			Pageable pageable
	);

	Optional<Notification> findByRecipientUserIdAndSourceEventId(
			UUID recipientUserId,
			UUID sourceEventId
	);

	Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

	@Modifying
	@Query("""
			UPDATE Notification notification
			SET notification.readAt = :readAt
			WHERE notification.recipientUserId = :userId
				AND notification.readAt IS NULL
			""")
	int markAllAsRead(
			@Param("userId") UUID userId,
			@Param("readAt") LocalDateTime readAt
	);
}
