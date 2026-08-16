package ru.itmo.nemat.weezzy.lunch.request;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.lunch.request.dto.CreateLunchRequest;
import ru.itmo.nemat.weezzy.lunch.request.dto.LunchRequestResponse;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

import java.net.URI;

@RestController
@RequestMapping("/api/lunch/requests")
@RequiredArgsConstructor
public class LunchRequestController {
	private final LunchRequestService service;

	@PostMapping
	public ResponseEntity<LunchRequestResponse> create(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@Valid @RequestBody CreateLunchRequest request
	) {
		LunchRequest lunchRequest = service.create(authenticatedUser.id(), request);
		return ResponseEntity
				.created(URI.create("/api/lunch/requests/me"))
				.body(LunchRequestResponse.from(lunchRequest));
	}

	@GetMapping("/me")
	public ResponseEntity<LunchRequestResponse> findCurrent(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		return ResponseEntity.ok(LunchRequestResponse.from(
				service.findActiveForUser(authenticatedUser.id())
		));
	}

	@DeleteMapping("/me")
	public ResponseEntity<Void> cancel(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		service.cancelCurrent(authenticatedUser.id());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/me/extend")
	public ResponseEntity<LunchRequestResponse> extend(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		return ResponseEntity.ok(LunchRequestResponse.from(
				service.extendCurrent(authenticatedUser.id())
		));
	}
}
