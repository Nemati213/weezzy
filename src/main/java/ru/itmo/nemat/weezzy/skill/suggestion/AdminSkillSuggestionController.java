package ru.itmo.nemat.weezzy.skill.suggestion;

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
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;
import ru.itmo.nemat.weezzy.skill.suggestion.dto.SkillSuggestionResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/skill-suggestions")
@RequiredArgsConstructor
public class AdminSkillSuggestionController {
	private final SkillSuggestionService service;

	@GetMapping
	public ResponseEntity<PageResponse<SkillSuggestionResponse>> findByStatus(
			@RequestParam(defaultValue = "PENDING") SkillSuggestionStatus status,
			@PageableDefault(size = 20) Pageable pageable
	) {
		Page<SkillSuggestionResponse> suggestions = service.findByStatus(status, pageable)
				.map(SkillSuggestionResponse::from);
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
