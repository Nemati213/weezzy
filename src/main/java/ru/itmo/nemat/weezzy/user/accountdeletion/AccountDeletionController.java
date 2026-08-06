package ru.itmo.nemat.weezzy.user.accountdeletion;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;
import ru.itmo.nemat.weezzy.user.accountdeletion.dto.DeleteAccountRequest;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AccountDeletionController {
	private final AccountDeletionService accountDeletionService;

	@DeleteMapping("/me")
	public ResponseEntity<Void> deleteAccount(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser,
			@Valid @RequestBody DeleteAccountRequest request
	) {
		accountDeletionService.deleteAccount(
				authenticatedUser.id(),
				request.currentPassword()
		);
		return ResponseEntity.noContent().build();
	}
}
