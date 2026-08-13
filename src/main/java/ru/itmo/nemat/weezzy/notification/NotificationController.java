package ru.itmo.nemat.weezzy.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.common.dto.CursorPageResponse;
import ru.itmo.nemat.weezzy.notification.dto.NotificationResponse;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/me")
@RequiredArgsConstructor
@Validated
public class NotificationController {
	private final NotificationService notificationService;

	@GetMapping
	public ResponseEntity<CursorPageResponse<NotificationResponse>> findCurrentUserNotifications(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
	) {
		return ResponseEntity.ok(notificationService.findPage(
				authenticatedUser.id(),
				cursor,
				limit
		));
	}

	@PatchMapping("/{notificationId}/read")
	public ResponseEntity<NotificationResponse> markAsRead(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@PathVariable UUID notificationId
	) {
		return ResponseEntity.ok(NotificationResponse.from(
				notificationService.markAsRead(
						authenticatedUser.id(),
						notificationId
				)
		));
	}

	@PatchMapping("/read-all")
	public ResponseEntity<Void> markAllAsRead(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		notificationService.markAllAsRead(authenticatedUser.id());
		return ResponseEntity.noContent().build();
	}
}
