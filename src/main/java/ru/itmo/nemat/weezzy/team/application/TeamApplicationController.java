package ru.itmo.nemat.weezzy.team.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.common.dto.PageResponse;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;
import ru.itmo.nemat.weezzy.team.application.dto.CreateTeamApplicationRequest;
import ru.itmo.nemat.weezzy.team.application.dto.TeamApplicationResponse;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
public class TeamApplicationController {
	private final TeamApplicationService service;

	@PostMapping("/api/team-request-positions/{positionId}/applications")
	public ResponseEntity<TeamApplicationResponse> send(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@PathVariable UUID positionId,
			@Valid @RequestBody CreateTeamApplicationRequest request
	) {
		TeamApplicationResponse response = service.send(user.id(), positionId, request);
		return ResponseEntity.created(URI.create("/api/team-applications/" + response.id()))
				.body(response);
	}

	@GetMapping("/api/team-applications/me")
	public ResponseEntity<PageResponse<TeamApplicationResponse>> findOwn(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@RequestParam(required = false) TeamApplicationStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return ResponseEntity.ok(service.findOwn(user.id(), status, pageable(page, size)));
	}

	@GetMapping("/api/team-requests/{requestId}/applications")
	public ResponseEntity<PageResponse<TeamApplicationResponse>> findForRequest(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@PathVariable UUID requestId,
			@RequestParam(required = false) UUID positionId,
			@RequestParam(required = false) TeamApplicationStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return ResponseEntity.ok(service.findForRequest(
				user.id(), requestId, positionId, status, pageable(page, size)));
	}

	@GetMapping("/api/team-applications/{id}")
	public ResponseEntity<TeamApplicationResponse> findById(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID id
	) {
		return ResponseEntity.ok(service.findByIdForUser(user.id(), id));
	}

	@PostMapping("/api/team-applications/{id}/accept")
	public ResponseEntity<TeamApplicationResponse> accept(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID id
	) {
		return ResponseEntity.ok(service.accept(user.id(), id));
	}

	@PostMapping("/api/team-applications/{id}/decline")
	public ResponseEntity<TeamApplicationResponse> decline(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID id
	) {
		return ResponseEntity.ok(service.decline(user.id(), id));
	}

	@PostMapping("/api/team-applications/{id}/withdraw")
	public ResponseEntity<TeamApplicationResponse> withdraw(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID id
	) {
		return ResponseEntity.ok(service.withdraw(user.id(), id));
	}

	private PageRequest pageable(int page, int size) {
		return PageRequest.of(page, size, Sort.by(
				Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
	}
}
