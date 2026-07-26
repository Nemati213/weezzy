package ru.itmo.nemat.weezzy.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.recommendation.dto.ProfileRecommendationResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles/{profileId}/recommendations")
@RequiredArgsConstructor
public class RecommendationController {
	private final RecommendationService recommendationService;

	@GetMapping
	public ResponseEntity<List<ProfileRecommendationResponse>> getRecommendations(
			@PathVariable UUID profileId,
			@RequestParam(defaultValue = "20") int limit
	) {
		return ResponseEntity.ok(recommendationService.findRecommendations(profileId, limit));
	}

}
