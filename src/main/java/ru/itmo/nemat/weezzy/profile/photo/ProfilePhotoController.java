package ru.itmo.nemat.weezzy.profile.photo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.profile.photo.dto.CreatePhotoUploadRequest;
import ru.itmo.nemat.weezzy.profile.photo.dto.PhotoUploadResponse;
import ru.itmo.nemat.weezzy.profile.photo.dto.ProfilePhotoResponse;
import ru.itmo.nemat.weezzy.profile.photo.dto.ReorderProfilePhotosRequest;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles/me/photos")
@RequiredArgsConstructor
public class ProfilePhotoController {
	private final ProfilePhotoService photoService;

	@PostMapping("/uploads")
	public ResponseEntity<PhotoUploadResponse> createUpload(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@Valid @RequestBody CreatePhotoUploadRequest request
	) {
		PhotoUploadResponse response = photoService.createUpload(
				authenticatedUser.id(),
				request
		);
		return ResponseEntity
				.created(URI.create("/api/profiles/me/photos/" + response.photoId()))
				.body(response);
	}

	@PostMapping("/{photoId}/confirm")
	public ResponseEntity<ProfilePhotoResponse> confirmUpload(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID photoId
	) {
		return ResponseEntity.ok(photoService.confirmUpload(
				authenticatedUser.id(),
				photoId
		));
	}

	@GetMapping
	public ResponseEntity<List<ProfilePhotoResponse>> getPhotos(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		return ResponseEntity.ok(photoService.getPhotos(authenticatedUser.id()));
	}

	@PatchMapping("/order")
	public ResponseEntity<List<ProfilePhotoResponse>> reorderPhotos(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@Valid @RequestBody ReorderProfilePhotosRequest request
	) {
		return ResponseEntity.ok(photoService.reorderPhotos(
				authenticatedUser.id(),
				request
		));
	}

	@PutMapping("/{photoId}/avatar")
	public ResponseEntity<ProfilePhotoResponse> setAvatar(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID photoId
	) {
		return ResponseEntity.ok(photoService.setAvatar(
				authenticatedUser.id(),
				photoId
		));
	}

	@DeleteMapping("/{photoId}")
	public ResponseEntity<Void> deletePhoto(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID photoId
	) {
		photoService.deletePhoto(authenticatedUser.id(), photoId);
		return ResponseEntity.noContent().build();
	}
}
