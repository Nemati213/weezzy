package ru.itmo.nemat.weezzy.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.recommendation.dto.RecommendationPageResponse;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {
	private final RecommendationService recommendationService;
	private final ProfileService profileService;

	@GetMapping
	public ResponseEntity<RecommendationPageResponse> getRecommendations(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@RequestParam(defaultValue = "20") int limit,
			@RequestParam(required = false) String cursor
	) {
		var profileId = profileService.findByUserId(authenticatedUser.id()).getId();
		return ResponseEntity.ok(recommendationService.findRecommendations(
				profileId,
				limit,
				cursor
		));
	}
}
