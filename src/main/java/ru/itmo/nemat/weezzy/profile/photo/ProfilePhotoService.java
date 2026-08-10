package ru.itmo.nemat.weezzy.profile.photo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.onboarding.OnboardingService;
import ru.itmo.nemat.weezzy.profile.Profile;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.profile.photo.dto.CreatePhotoUploadRequest;
import ru.itmo.nemat.weezzy.profile.photo.dto.PhotoUploadResponse;
import ru.itmo.nemat.weezzy.profile.photo.dto.ProfilePhotoResponse;
import ru.itmo.nemat.weezzy.profile.photo.dto.ReorderProfilePhotosRequest;
import ru.itmo.nemat.weezzy.storage.ObjectStorageService;
import ru.itmo.nemat.weezzy.storage.dto.PresignedDownload;
import ru.itmo.nemat.weezzy.storage.dto.PresignedUpload;
import ru.itmo.nemat.weezzy.storage.dto.StoredObjectMetadata;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfilePhotoService {
	private static final String OBJECT_KEY_TEMPLATE = "profiles/%s/%s";

	private final ProfilePhotoRepository photoRepository;
	private final ProfileService profileService;
	private final ObjectStorageService storageService;
	private final ProfilePhotoProperties properties;
	private final OnboardingService onboardingService;

	@Transactional
	public PhotoUploadResponse createUpload(
			UUID userId,
			CreatePhotoUploadRequest request
	) {
		String contentType = normalizeContentType(request.contentType());
		validateUpload(contentType, request.sizeBytes());

		Profile profile = profileService.findByUserIdForUpdate(userId);
		deleteExpiredPendingPhotos(profile.getId());
		validatePhotoLimit(profile);

		ProfilePhoto photo = new ProfilePhoto();
		photo.setProfile(profile);
		photo.setObjectKey(createObjectKey(profile.getId()));
		photo.setContentType(contentType);
		photo.setSizeBytes(request.sizeBytes());
		photo.setPosition(nextPosition(profile.getId()));
		photo.setIsAvatar(false);
		photo.setStatus(ProfilePhotoStatus.PENDING);
		photoRepository.save(photo);

		PresignedUpload upload = storageService.createUpload(
				photo.getObjectKey(),
				photo.getContentType(),
				photo.getSizeBytes()
		);

		return new PhotoUploadResponse(
				photo.getId(),
				upload.uploadUrl(),
				upload.expiresAt()
		);
	}

	@Transactional
	public ProfilePhotoResponse confirmUpload(UUID userId, UUID photoId) {
		Profile profile = profileService.findByUserIdForUpdate(userId);
		ProfilePhoto photo = findOwnedPhoto(photoId, profile.getId());

		if (photo.getStatus() == ProfilePhotoStatus.READY) {
			return toResponse(photo);
		}

		StoredObjectMetadata metadata = storageService
				.getMetadata(photo.getObjectKey())
				.orElseThrow(() -> new PhotoUploadNotFoundException(photoId));
		if (!metadataMatches(photo, metadata)) {
			storageService.deleteObject(photo.getObjectKey());
			throw new PhotoMetadataMismatchException(photoId);
		}

		boolean firstReadyPhoto = !photoRepository.existsByProfileIdAndStatus(
				profile.getId(),
				ProfilePhotoStatus.READY
		);
		photo.setStatus(ProfilePhotoStatus.READY);
		photo.setUploadedAt(LocalDateTime.now());
		photo.setIsAvatar(firstReadyPhoto);

		return toResponse(photo);
	}

	@Transactional(readOnly = true)
	public List<ProfilePhotoResponse> getPhotos(UUID userId) {
		Profile profile = profileService.findByUserId(userId);
		return findReadyPhotos(profile.getId());
	}

	@Transactional(readOnly = true)
	public List<ProfilePhotoResponse> findReadyPhotos(UUID profileId) {
		return toResponses(photoRepository
				.findAllByProfileIdAndStatusOrderByPositionAsc(
						profileId,
						ProfilePhotoStatus.READY
				));
	}

	@Transactional(readOnly = true)
	public Map<UUID, List<ProfilePhotoResponse>> findReadyPhotosByProfileIds(
			Collection<UUID> profileIds
	) {
		if (profileIds.isEmpty()) {
			return Map.of();
		}

		return photoRepository
				.findAllByProfileIdInAndStatusOrderByProfileIdAscPositionAsc(
						profileIds,
						ProfilePhotoStatus.READY
				)
				.stream()
				.collect(Collectors.groupingBy(
						photo -> photo.getProfile().getId(),
						Collectors.mapping(this::toResponse, Collectors.toList())
				));
	}

	@Transactional
	public List<ProfilePhotoResponse> reorderPhotos(
			UUID userId,
			ReorderProfilePhotosRequest request
	) {
		Profile profile = profileService.findByUserIdForUpdate(userId);
		List<ProfilePhoto> photos = photoRepository
				.findAllByProfileIdAndStatusOrderByPositionAsc(
						profile.getId(),
						ProfilePhotoStatus.READY
				);
		validateOrder(request.photoIds(), photos);

		Map<UUID, ProfilePhoto> photosById = new HashMap<>();
		photos.forEach(photo -> photosById.put(photo.getId(), photo));
		List<ProfilePhoto> reordered = request.photoIds().stream()
				.map(photosById::get)
				.toList();
		for (int position = 0; position < reordered.size(); position++) {
			reordered.get(position).setPosition(position);
		}
		movePendingPhotosAfter(profile.getId(), reordered.size());

		return toResponses(reordered);
	}

	@Transactional
	public ProfilePhotoResponse setAvatar(UUID userId, UUID photoId) {
		Profile profile = profileService.findByUserIdForUpdate(userId);
		ProfilePhoto target = findOwnedPhoto(photoId, profile.getId());
		ensureReady(target);
		if (Boolean.TRUE.equals(target.getIsAvatar())) {
			return toResponse(target);
		}

		photoRepository.findByProfileIdAndIsAvatarTrue(profile.getId())
				.ifPresent(photo -> photo.setIsAvatar(false));
		photoRepository.flush();
		target.setIsAvatar(true);

		return toResponse(target);
	}

	@Transactional
	public void deletePhoto(UUID userId, UUID photoId) {
		Profile profile = profileService.findByUserIdForUpdate(userId);
		ProfilePhoto photo = findOwnedPhoto(photoId, profile.getId());
		boolean deletedAvatar = Boolean.TRUE.equals(photo.getIsAvatar());

		storageService.deleteObject(photo.getObjectKey());
		photoRepository.delete(photo);
		photoRepository.flush();

		if (deletedAvatar) {
			photoRepository.findFirstByProfileIdAndStatusOrderByPositionAsc(
						profile.getId(),
						ProfilePhotoStatus.READY
				).ifPresent(nextAvatar -> nextAvatar.setIsAvatar(true));
		}
		compactPositions(profile.getId());
		onboardingService.moveToDraftIfIncomplete(profile);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void deleteAllForProfile(UUID profileId) {
		List<ProfilePhoto> photos =
				photoRepository.findAllByProfileIdOrderByPositionAsc(profileId);
		photos.forEach(photo -> storageService.deleteObject(photo.getObjectKey()));
		photoRepository.deleteAll(photos);
		photoRepository.flush();
	}

	private String normalizeContentType(String contentType) {
		return contentType.trim().toLowerCase(Locale.ROOT);
	}

	private void validateUpload(String contentType, long sizeBytes) {
		if (!properties.allowedContentTypes().contains(contentType)) {
			throw new UnsupportedPhotoContentTypeException(contentType);
		}
		if (sizeBytes > properties.maxFileSize()) {
			throw new PhotoTooLargeException(sizeBytes, properties.maxFileSize());
		}
	}

	private void validatePhotoLimit(Profile profile) {
		long photoCount = photoRepository.countByProfileId(profile.getId());
		if (photoCount >= properties.maxPhotos()) {
			throw new PhotoLimitExceededException(
					profile.getId(),
					properties.maxPhotos()
			);
		}
	}

	private void validateOrder(
			List<UUID> requestedIds,
			List<ProfilePhoto> photos
	) {
		Set<UUID> requestedSet = new HashSet<>(requestedIds);
		Set<UUID> existingSet = photos.stream()
				.map(ProfilePhoto::getId)
				.collect(Collectors.toSet());
		if (requestedIds.size() != requestedSet.size()
				|| !requestedSet.equals(existingSet)) {
			throw new InvalidPhotoOrderException();
		}
	}

	private boolean metadataMatches(
			ProfilePhoto photo,
			StoredObjectMetadata metadata
	) {
		return Objects.equals(photo.getContentType(), metadata.contentType())
				&& photo.getSizeBytes().longValue() == metadata.sizeBytes()
				&& metadata.sizeBytes() <= properties.maxFileSize();
	}

	private ProfilePhoto findOwnedPhoto(UUID photoId, UUID profileId) {
		return photoRepository.findByIdAndProfileId(photoId, profileId)
				.orElseThrow(() -> new ProfilePhotoNotFoundException(photoId));
	}

	private void ensureReady(ProfilePhoto photo) {
		if (photo.getStatus() != ProfilePhotoStatus.READY) {
			throw new ProfilePhotoNotReadyException(photo.getId());
		}
	}

	private int nextPosition(UUID profileId) {
		return photoRepository.findTopByProfileIdOrderByPositionDesc(profileId)
				.map(photo -> photo.getPosition() + 1)
				.orElse(0);
	}

	private void compactPositions(UUID profileId) {
		List<ProfilePhoto> photos =
				photoRepository.findAllByProfileIdOrderByPositionAsc(profileId);
		for (int position = 0; position < photos.size(); position++) {
			photos.get(position).setPosition(position);
		}
	}

	private void movePendingPhotosAfter(UUID profileId, int firstPosition) {
		List<ProfilePhoto> pendingPhotos = photoRepository
				.findAllByProfileIdAndStatusOrderByPositionAsc(
						profileId,
						ProfilePhotoStatus.PENDING
				);
		for (int index = 0; index < pendingPhotos.size(); index++) {
			pendingPhotos.get(index).setPosition(firstPosition + index);
		}
	}

	private void deleteExpiredPendingPhotos(UUID profileId) {
		LocalDateTime createdBefore = LocalDateTime.now()
				.minus(properties.pendingTtl());
		List<ProfilePhoto> expiredPhotos = photoRepository
				.findAllByProfileIdAndStatusAndCreatedAtBefore(
						profileId,
						ProfilePhotoStatus.PENDING,
						createdBefore
				);
		expiredPhotos.forEach(photo ->
				storageService.deleteObject(photo.getObjectKey()));
		photoRepository.deleteAll(expiredPhotos);
		if (!expiredPhotos.isEmpty()) {
			photoRepository.flush();
			compactPositions(profileId);
		}
	}

	private String createObjectKey(UUID profileId) {
		return OBJECT_KEY_TEMPLATE.formatted(profileId, UUID.randomUUID());
	}

	private List<ProfilePhotoResponse> toResponses(List<ProfilePhoto> photos) {
		return photos.stream().map(this::toResponse).toList();
	}

	private ProfilePhotoResponse toResponse(ProfilePhoto photo) {
		PresignedDownload download = storageService.createDownload(
				photo.getObjectKey()
		);
		return new ProfilePhotoResponse(
				photo.getId(),
				download.downloadUrl(),
				download.expiresAt(),
				photo.getContentType(),
				photo.getSizeBytes(),
				photo.getPosition(),
				Boolean.TRUE.equals(photo.getIsAvatar())
		);
	}
}
