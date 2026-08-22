package ru.itmo.nemat.weezzy.lunch.group;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.lunch.group.dto.LunchGroupResponse;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

@RestController
@RequestMapping("/api/lunch/groups")
@RequiredArgsConstructor
public class LunchGroupController {
	private final LunchGroupService service;

	@GetMapping("/me")
	public ResponseEntity<LunchGroupResponse> findCurrent(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		return ResponseEntity.ok(service.findCurrentForUser(authenticatedUser.id()));
	}
}
