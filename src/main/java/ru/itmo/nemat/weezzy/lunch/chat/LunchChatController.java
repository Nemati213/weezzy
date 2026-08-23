package ru.itmo.nemat.weezzy.lunch.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.lunch.chat.dto.CreateLunchChatMessageRequest;
import ru.itmo.nemat.weezzy.lunch.chat.dto.LunchChatMessagePageResponse;
import ru.itmo.nemat.weezzy.lunch.chat.dto.LunchChatMessageResponse;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

@RestController
@RequestMapping("/api/lunch/groups/me/messages")
@RequiredArgsConstructor
@Validated
public class LunchChatController {
	private final LunchChatService service;

	@PostMapping
	public ResponseEntity<LunchChatMessageResponse> send(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@Valid @RequestBody CreateLunchChatMessageRequest request
	) {
		LunchChatSendResult result = service.send(authenticatedUser.id(), request);
		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(
				LunchChatMessageResponse.from(result.message())
		);
	}

	@GetMapping
	public ResponseEntity<LunchChatMessagePageResponse> findPage(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@RequestParam(required = false) String before,
			@RequestParam(required = false) String after,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
	) {
		return ResponseEntity.ok(service.findPage(
				authenticatedUser.id(),
				before,
				after,
				limit
		));
	}
}
