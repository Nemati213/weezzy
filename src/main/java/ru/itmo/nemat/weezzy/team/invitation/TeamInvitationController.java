package ru.itmo.nemat.weezzy.team.invitation;

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
import ru.itmo.nemat.weezzy.team.invitation.dto.CreateTeamInvitationRequest;
import ru.itmo.nemat.weezzy.team.invitation.dto.TeamInvitationResponse;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
public class TeamInvitationController {
	private final TeamInvitationService service;

	@PostMapping("/api/teams/{teamId}/invitations")
	public ResponseEntity<TeamInvitationResponse> send(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@PathVariable UUID teamId,
			@Valid @RequestBody CreateTeamInvitationRequest request
	) {
		TeamInvitationResponse invitation = service.send(
				user.id(), teamId, request.inviteeProfileId()
		);
		return ResponseEntity.created(URI.create("/api/team-invitations/" + invitation.id()))
				.body(invitation);
	}

	@GetMapping("/api/team-invitations/me")
	public ResponseEntity<PageResponse<TeamInvitationResponse>> findIncoming(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@RequestParam(defaultValue = "PENDING") TeamInvitationStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return ResponseEntity.ok(service.findIncoming(user.id(), status, PageRequest.of(
				page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending())
		)));
	}

	@GetMapping("/api/team-invitations/{id}")
	public ResponseEntity<TeamInvitationResponse> getInvitation(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID id
	) {
		return ResponseEntity.ok(service.findByIdForUser(user.id(), id));
	}

	@PostMapping("/api/team-invitations/{id}/accept")
	public ResponseEntity<TeamInvitationResponse> accept(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID id
	) {
		return ResponseEntity.ok(service.accept(user.id(), id));
	}

	@PostMapping("/api/team-invitations/{id}/decline")
	public ResponseEntity<TeamInvitationResponse> decline(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID id
	) {
		return ResponseEntity.ok(service.decline(user.id(), id));
	}

	@PostMapping("/api/team-invitations/{id}/cancel")
	public ResponseEntity<TeamInvitationResponse> cancel(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID id
	) {
		return ResponseEntity.ok(service.cancel(user.id(), id));
	}
}
