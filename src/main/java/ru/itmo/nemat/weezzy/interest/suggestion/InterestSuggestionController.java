package ru.itmo.nemat.weezzy.interest.suggestion;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.interest.suggestion.dto.CreateInterestSuggestionRequest;
import ru.itmo.nemat.weezzy.interest.suggestion.dto.InterestSuggestionResponse;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/interest-suggestions")
@RequiredArgsConstructor
public class InterestSuggestionController {
	private final InterestSuggestionService service;

	@PostMapping
	public ResponseEntity<InterestSuggestionResponse> create(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@Valid @RequestBody CreateInterestSuggestionRequest request
	) {
		InterestSuggestion suggestion = service.create(authenticatedUser.id(), request);
		return ResponseEntity
				.created(URI.create("/api/interest-suggestions/" + suggestion.getId()))
				.body(InterestSuggestionResponse.from(suggestion));
	}

	@GetMapping("/me")
	public ResponseEntity<List<InterestSuggestionResponse>> getMine(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		return ResponseEntity.ok(service.findByUserId(authenticatedUser.id()).stream()
				.map(InterestSuggestionResponse::from)
				.toList());
	}
}
