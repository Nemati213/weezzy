package ru.itmo.nemat.weezzy.connection.vote;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.connection.vote.dto.CreateVoteRequest;
import ru.itmo.nemat.weezzy.connection.vote.dto.VoteResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ProfileVoteController {
	private final ProfileVoteService service;

	@PostMapping("/{sourceProfileId}/votes/{targetProfileId}")
	public ResponseEntity<VoteResponse> addVote(
			@PathVariable UUID sourceProfileId,
			@PathVariable UUID targetProfileId,
			@Valid @RequestBody CreateVoteRequest request
	) {
		return ResponseEntity.ok(VoteResponse.from(service.vote(sourceProfileId, targetProfileId, request.action())));
	}

	@GetMapping("/{sourceProfileId}/votes")
	public ResponseEntity<List<VoteResponse>> getAllVotes(@PathVariable UUID sourceProfileId) {
		return ResponseEntity.ok(service.findBySourceProfileId(sourceProfileId).stream()
				.map(VoteResponse::from)
				.toList());
	}
}
