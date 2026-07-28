package ru.itmo.nemat.weezzy.skill.suggestion;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;
import ru.itmo.nemat.weezzy.skill.suggestion.dto.CreateSkillSuggestionRequest;
import ru.itmo.nemat.weezzy.skill.suggestion.dto.SkillSuggestionResponse;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/skill-suggestions")
@RequiredArgsConstructor
public class SkillSuggestionController {
	private final SkillSuggestionService service;

	@PostMapping
	public ResponseEntity<SkillSuggestionResponse> create(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@Valid @RequestBody CreateSkillSuggestionRequest request
	) {
		SkillSuggestion suggestion = service.create(authenticatedUser.id(), request);
		return ResponseEntity
				.created(URI.create("/api/skill-suggestions/" + suggestion.getId()))
				.body(SkillSuggestionResponse.from(suggestion));
	}

	@GetMapping("/me")
	public ResponseEntity<List<SkillSuggestionResponse>> getMine(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		return ResponseEntity.ok(service.findByUserId(authenticatedUser.id()).stream()
				.map(SkillSuggestionResponse::from)
				.toList());
	}
}
