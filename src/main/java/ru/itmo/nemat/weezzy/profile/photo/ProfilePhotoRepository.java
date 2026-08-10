package ru.itmo.nemat.weezzy.profile.photo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfilePhotoRepository extends JpaRepository<ProfilePhoto, UUID> {
	long countByProfileId(UUID profileId);

	Optional<ProfilePhoto> findTopByProfileIdOrderByPositionDesc(UUID profileId);

	Optional<ProfilePhoto> findByIdAndProfileId(
			UUID photoId,
			UUID profileId
	);

	Optional<ProfilePhoto> findByProfileIdAndIsAvatarTrue(UUID profileId);

	Optional<ProfilePhoto> findFirstByProfileIdAndStatusOrderByPositionAsc(
			UUID profileId,
			ProfilePhotoStatus status
	);

	boolean existsByProfileIdAndStatus(
			UUID profileId,
			ProfilePhotoStatus status
	);

	List<ProfilePhoto> findAllByProfileIdOrderByPositionAsc(UUID profileId);

	List<ProfilePhoto> findAllByProfileIdAndStatusOrderByPositionAsc(
			UUID profileId,
			ProfilePhotoStatus status
	);

	List<ProfilePhoto> findAllByProfileIdAndStatusAndCreatedAtBefore(
			UUID profileId,
			ProfilePhotoStatus status,
			LocalDateTime createdBefore
	);
}
