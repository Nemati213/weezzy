package ru.itmo.nemat.weezzy.team.request;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.common.dto.PageResponse;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;
import ru.itmo.nemat.weezzy.team.request.dto.CreateRecruitmentRequest;
import ru.itmo.nemat.weezzy.team.request.dto.TeamRequestResponse;
import ru.itmo.nemat.weezzy.team.request.dto.UpdateRecruitmentRequest;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
public class TeamRequestController {
	private final TeamRequestService service;

	@PostMapping("/api/teams/{teamId}/requests")
	public ResponseEntity<TeamRequestResponse> create(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@PathVariable UUID teamId,
			@Valid @RequestBody CreateRecruitmentRequest request
	) {
		TeamRequestResponse response = service.createForUser(user.id(), teamId, request);
		return ResponseEntity.created(URI.create("/api/team-requests/" + response.id()))
				.body(response);
	}

	@GetMapping("/api/team-requests/{requestId}")
	public ResponseEntity<TeamRequestResponse> findById(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@PathVariable UUID requestId
	) {
		return ResponseEntity.ok(service.findByIdForUser(user.id(), requestId));
	}

	@GetMapping("/api/teams/{teamId}/requests/me")
	public ResponseEntity<PageResponse<TeamRequestResponse>> findByTeam(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@PathVariable UUID teamId,
			@RequestParam(required = false) TeamRequestStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return ResponseEntity.ok(service.findByTeamForUser(user.id(), teamId, status,
				PageRequest.of(page, size, Sort.by(
						Sort.Order.desc("createdAt"), Sort.Order.asc("id")
				))));
	}

	@PutMapping("/api/team-requests/{requestId}")
	public ResponseEntity<TeamRequestResponse> update(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@PathVariable UUID requestId,
			@Valid @RequestBody UpdateRecruitmentRequest request
	) {
		return ResponseEntity.ok(service.updateForUser(user.id(), requestId, request));
	}

	@PostMapping("/api/team-requests/{requestId}/close")
	public ResponseEntity<TeamRequestResponse> close(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@PathVariable UUID requestId
	) {
		return ResponseEntity.ok(service.closeForUser(user.id(), requestId));
	}
}
