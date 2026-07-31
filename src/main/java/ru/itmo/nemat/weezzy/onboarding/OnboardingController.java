package ru.itmo.nemat.weezzy.onboarding;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.onboarding.dto.OnboardingResponse;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {
	private final OnboardingService service;

	@GetMapping("/me")
	public ResponseEntity<OnboardingResponse> findCurrentUserOnboarding(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		return ResponseEntity.ok(service.findForUser(authenticatedUser.id()));
	}
}
