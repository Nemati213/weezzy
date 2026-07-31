package ru.itmo.nemat.weezzy.connection.block;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.connection.block.dto.ProfileBlockResponse;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
public class ProfileBlockController {
	private final ProfileBlockService blockService;
	private final ProfileService profileService;

	@PostMapping("/{blockedProfileId}")
	public ResponseEntity<ProfileBlockResponse> blockProfile(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID blockedProfileId
	) {
		return ResponseEntity.ok(blockService.block(
				currentProfileId(authenticatedUser),
				blockedProfileId
		));
	}

	@GetMapping
	public ResponseEntity<List<ProfileBlockResponse>> findCurrentProfileBlocks(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		return ResponseEntity.ok(blockService.findBlocksByProfileId(
				currentProfileId(authenticatedUser)
		));
	}

	@DeleteMapping("/{blockedProfileId}")
	public ResponseEntity<Void> unblockProfile(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID blockedProfileId
	) {
		blockService.unblock(currentProfileId(authenticatedUser), blockedProfileId);
		return ResponseEntity.noContent().build();
	}

	private UUID currentProfileId(JwtAuthenticatedUser authenticatedUser) {
		return profileService.findByUserId(authenticatedUser.id()).getId();
	}
}
