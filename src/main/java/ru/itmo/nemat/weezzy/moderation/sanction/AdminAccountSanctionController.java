package ru.itmo.nemat.weezzy.moderation.sanction;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.common.dto.PageResponse;
import ru.itmo.nemat.weezzy.moderation.sanction.dto.AccountSanctionResponse;
import ru.itmo.nemat.weezzy.moderation.sanction.dto.CreateAccountSanctionRequest;
import ru.itmo.nemat.weezzy.moderation.sanction.dto.RevokeAccountSanctionRequest;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAccountSanctionController {
	private final AccountSanctionService sanctionService;

	@PostMapping("/users/{targetUserId}/sanctions")
	public ResponseEntity<AccountSanctionResponse> create(
			@PathVariable UUID targetUserId,
			@AuthenticationPrincipal JwtAuthenticatedUser adminUser,
			@Valid @RequestBody CreateAccountSanctionRequest request
	) {
		AccountSanction sanction = sanctionService.create(
				targetUserId,
				adminUser.id(),
				request
		);
		return ResponseEntity
				.created(URI.create("/api/admin/sanctions/" + sanction.getId()))
				.body(AccountSanctionResponse.from(sanction));
	}

	@GetMapping("/sanctions")
	public ResponseEntity<PageResponse<AccountSanctionResponse>> findByStatus(
			@RequestParam(defaultValue = "ACTIVE") AccountSanctionStatus status,
			@PageableDefault(size = 20) Pageable pageable
	) {
		Page<AccountSanctionResponse> sanctions = sanctionService
				.findByStatus(status, pageable)
				.map(AccountSanctionResponse::from);
		return ResponseEntity.ok(PageResponse.from(sanctions));
	}

	@GetMapping("/sanctions/{sanctionId}")
	public ResponseEntity<AccountSanctionResponse> findById(
			@PathVariable UUID sanctionId
	) {
		return ResponseEntity.ok(AccountSanctionResponse.from(
				sanctionService.findById(sanctionId)
		));
	}

	@GetMapping("/users/{targetUserId}/sanctions")
	public ResponseEntity<PageResponse<AccountSanctionResponse>> findByTargetUserId(
			@PathVariable UUID targetUserId,
			@PageableDefault(size = 20) Pageable pageable
	) {
		Page<AccountSanctionResponse> sanctions = sanctionService
				.findByTargetUserId(targetUserId, pageable)
				.map(AccountSanctionResponse::from);
		return ResponseEntity.ok(PageResponse.from(sanctions));
	}

	@PatchMapping("/sanctions/{sanctionId}/revoke")
	public ResponseEntity<AccountSanctionResponse> revoke(
			@PathVariable UUID sanctionId,
			@AuthenticationPrincipal JwtAuthenticatedUser adminUser,
			@Valid @RequestBody RevokeAccountSanctionRequest request
	) {
		return ResponseEntity.ok(AccountSanctionResponse.from(
				sanctionService.revoke(
						sanctionId,
						adminUser.id(),
						request
				)
		));
	}
}
