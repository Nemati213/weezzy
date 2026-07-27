package ru.itmo.nemat.weezzy.connection.vote;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.connection.vote.dto.CreateVoteRequest;
import ru.itmo.nemat.weezzy.connection.vote.dto.VoteResponse;
import ru.itmo.nemat.weezzy.profile.ProfileService;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/votes")
@RequiredArgsConstructor
public class ProfileVoteController {
	private final ProfileVoteService service;
	private final ProfileService profileService;

	@PostMapping("/{targetProfileId}")
	public ResponseEntity<VoteResponse> addVote(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID targetProfileId,
			@Valid @RequestBody CreateVoteRequest request
	) {
		UUID sourceProfileId = currentProfileId(authenticatedUser);
		return ResponseEntity.ok(VoteResponse.from(service.vote(sourceProfileId, targetProfileId, request.action())));
	}

	@GetMapping
	public ResponseEntity<List<VoteResponse>> getAllVotes(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		return ResponseEntity.ok(service.findBySourceProfileId(currentProfileId(authenticatedUser)).stream()
				.map(VoteResponse::from)
				.toList());
	}

	private UUID currentProfileId(JwtAuthenticatedUser authenticatedUser) {
		return profileService.findByUserId(authenticatedUser.id()).getId();
	}
}
