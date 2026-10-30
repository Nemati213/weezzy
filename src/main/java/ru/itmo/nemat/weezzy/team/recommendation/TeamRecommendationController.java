package ru.itmo.nemat.weezzy.team.recommendation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;
import ru.itmo.nemat.weezzy.team.recommendation.dto.CreateTeamRecommendationSessionRequest;
import ru.itmo.nemat.weezzy.team.recommendation.dto.TeamRecommendationPageResponse;
import ru.itmo.nemat.weezzy.team.recommendation.dto.RecordTeamRecommendationImpressionsRequest;
import ru.itmo.nemat.weezzy.team.recommendation.impression.TeamRecommendationImpressionService;
import ru.itmo.nemat.weezzy.team.recommendation.page.TeamRecommendationPageService;
import ru.itmo.nemat.weezzy.team.recommendation.session.TeamRecommendationSessionService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/team-recommendations/me/sessions")
@RequiredArgsConstructor
public class TeamRecommendationController {
	private final TeamRecommendationSessionService sessions;
	private final TeamRecommendationPageService pages;
	private final TeamRecommendationImpressionService impressions;

	@PostMapping("/{sessionId}/impressions")
	public ResponseEntity<Void> recordImpressions(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@PathVariable UUID sessionId,
			@Valid @RequestBody RecordTeamRecommendationImpressionsRequest request
	) {
		impressions.recordForUser(user.id(), sessionId, request.entryIds());
		return ResponseEntity.noContent()
				.cacheControl(CacheControl.noStore().cachePrivate()).build();
	}

	@PostMapping
	public ResponseEntity<TeamRecommendationPageResponse> create(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@Valid @RequestBody CreateTeamRecommendationSessionRequest request
	) {
		var session = sessions.createForUser(
				user.id(), request.clientRequestId(), request.pageSize());
		var page = pages.findForUser(user.id(), session.sessionId(), null);
		return ResponseEntity
				.status(session.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.location(URI.create("/api/team-recommendations/me/sessions/"
						+ session.sessionId()))
				.cacheControl(CacheControl.noStore().cachePrivate())
				.body(page);
	}

	@GetMapping("/{sessionId}")
	public ResponseEntity<TeamRecommendationPageResponse> find(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@PathVariable UUID sessionId,
			@RequestParam(required = false) String cursor
	) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().cachePrivate())
				.body(pages.findForUser(user.id(), sessionId, cursor));
	}
}
