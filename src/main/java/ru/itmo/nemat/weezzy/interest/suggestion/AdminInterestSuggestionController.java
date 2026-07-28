package ru.itmo.nemat.weezzy.interest.suggestion;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.common.dto.PageResponse;
import ru.itmo.nemat.weezzy.interest.suggestion.dto.InterestSuggestionResponse;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/interest-suggestions")
@RequiredArgsConstructor
public class AdminInterestSuggestionController {
	private final InterestSuggestionService service;

	@GetMapping
	public ResponseEntity<PageResponse<InterestSuggestionResponse>> findByStatus(
			@RequestParam(defaultValue = "PENDING") InterestSuggestionStatus status,
			@PageableDefault(size = 20) Pageable pageable
	) {
		Page<InterestSuggestionResponse> suggestions = service.findByStatus(status, pageable)
				.map(InterestSuggestionResponse::from);
		return ResponseEntity.ok(PageResponse.from(suggestions));
	}

	@PatchMapping("/{id}/approve")
	public ResponseEntity<Void> approve(
			@PathVariable UUID id,
			@AuthenticationPrincipal JwtAuthenticatedUser adminUser
	) {
		service.approve(id, adminUser.id());
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/{id}/reject")
	public ResponseEntity<Void> reject(
			@PathVariable UUID id,
			@AuthenticationPrincipal JwtAuthenticatedUser adminUser
	) {
		service.reject(id, adminUser.id());
		return ResponseEntity.noContent().build();
	}
}
