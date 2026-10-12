package ru.itmo.nemat.weezzy.team;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;
import ru.itmo.nemat.weezzy.team.dto.CreateTeamRequest;
import ru.itmo.nemat.weezzy.team.dto.TeamResponse;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {
	private final TeamService service;

	@PostMapping
	public ResponseEntity<TeamResponse> createTeam(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@Valid @RequestBody CreateTeamRequest request
	) {
		TeamResponse team = service.createForUser(authenticatedUser.id(), request);
		return ResponseEntity.created(URI.create("/api/teams/" + team.id())).body(team);
	}

	@GetMapping("/{teamId}")
	public ResponseEntity<TeamResponse> getTeam(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID teamId
	) {
		return ResponseEntity.ok(service.findByIdForUser(authenticatedUser.id(), teamId));
	}

	@DeleteMapping("/{teamId}/members/me")
	public ResponseEntity<Void> leaveTeam(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID teamId
	) {
		service.leaveForUser(user.id(), teamId);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{teamId}/members/{profileId}")
	public ResponseEntity<Void> removeMember(
			@AuthenticationPrincipal JwtAuthenticatedUser user,
			@PathVariable UUID teamId, @PathVariable UUID profileId
	) {
		service.removeMemberForUser(user.id(), teamId, profileId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{teamId}/archive")
	public ResponseEntity<Void> archiveTeam(
			@AuthenticationPrincipal JwtAuthenticatedUser user, @PathVariable UUID teamId
	) {
		service.archiveForUser(user.id(), teamId);
		return ResponseEntity.noContent().build();
	}
}
