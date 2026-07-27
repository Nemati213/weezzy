package ru.itmo.nemat.weezzy.connection.match;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.connection.match.dto.ProfileMatchResponse;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class ProfileMatchController {
	private final ProfileMatchService service;
	private final ProfileService profileService;

	@GetMapping
	public ResponseEntity<List<ProfileMatchResponse>> findAllMatchesByProfileId(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		var profileId = profileService.findByUserId(authenticatedUser.id()).getId();
		return ResponseEntity.ok(service.findAllMatchesByProfileId(profileId));
	}
}
