package ru.itmo.nemat.weezzy.connection.match;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.connection.match.dto.ProfileMatchResponse;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class ProfileMatchController {
	private final ProfileMatchService matchService;
	private final ProfileService profileService;

	@GetMapping
	public ResponseEntity<List<ProfileMatchResponse>> findCurrentProfileMatches(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		var currentProfileId = currentProfileId(authenticatedUser);
		return ResponseEntity.ok(matchService.findAllMatchesByProfileId(currentProfileId));
	}

	@DeleteMapping("/{matchedProfileId}")
	public ResponseEntity<Void> unmatch(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID matchedProfileId
	) {
		var currentProfileId = currentProfileId(authenticatedUser);
		matchService.unmatch(currentProfileId, matchedProfileId);
		return ResponseEntity.noContent().build();
	}

	private UUID currentProfileId(JwtAuthenticatedUser authenticatedUser) {
		return profileService.findByUserId(authenticatedUser.id()).getId();
	}
}
