package ru.itmo.nemat.weezzy.team.position;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;
import ru.itmo.nemat.weezzy.team.position.dto.CreateTeamPositionRequest;
import ru.itmo.nemat.weezzy.team.position.dto.UpdateTeamPositionRequest;
import ru.itmo.nemat.weezzy.team.position.dto.TeamPositionResponse;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams/{teamId}/positions")
@RequiredArgsConstructor
public class TeamPositionController {
	private final TeamPositionService service;

	@PostMapping
	public ResponseEntity<TeamPositionResponse> create(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID teamId,
			@Valid @RequestBody CreateTeamPositionRequest request
	) {
		TeamPositionResponse response = service.createForUser(user.id(), teamId, request);
		return ResponseEntity.created(URI.create(
				"/api/teams/" + teamId + "/positions/" + response.id())).body(response);
	}

	@GetMapping
	public List<TeamPositionResponse> findAll(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID teamId
	) {
		return service.findForUser(user.id(), teamId);
	}

	@GetMapping("/{positionId}")
	public TeamPositionResponse findById(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID teamId,
			@PathVariable UUID positionId
	) {
		return service.findByIdForUser(user.id(), teamId, positionId);
	}

	@PutMapping("/{positionId}")
	public TeamPositionResponse update(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID teamId,
			@PathVariable UUID positionId, @Valid @RequestBody UpdateTeamPositionRequest request
	) {
		return service.updateForUser(user.id(), teamId, positionId, request);
	}
}
