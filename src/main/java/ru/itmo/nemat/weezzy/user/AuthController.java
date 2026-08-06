package ru.itmo.nemat.weezzy.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;
import ru.itmo.nemat.weezzy.user.dto.AuthTokenResponse;
import ru.itmo.nemat.weezzy.user.dto.AuthUserResponse;
import ru.itmo.nemat.weezzy.user.dto.LoginRequest;
import ru.itmo.nemat.weezzy.user.dto.RefreshTokenRequest;
import ru.itmo.nemat.weezzy.user.dto.RegisterRequest;
import ru.itmo.nemat.weezzy.user.dto.RegistrationResponse;
import ru.itmo.nemat.weezzy.user.dto.ResendEmailVerificationRequest;
import ru.itmo.nemat.weezzy.user.dto.VerifyEmailRequest;
import ru.itmo.nemat.weezzy.user.passwordreset.dto.ForgotPasswordRequest;
import ru.itmo.nemat.weezzy.user.passwordreset.dto.ResetPasswordRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
	private final AuthService authService;
	private final UserService userService;

	@PostMapping("/register")
	public ResponseEntity<RegistrationResponse> register(
			@Valid @RequestBody RegisterRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(
				authService.register(request.email(), request.password())
		);
	}

	@PostMapping("/login")
	public ResponseEntity<AuthTokenResponse> login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest
	) {
		return ResponseEntity.ok(authService.login(
				request.email(),
				request.password(),
				httpRequest.getHeader(HttpHeaders.USER_AGENT),
				httpRequest.getRemoteAddr()
		));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthTokenResponse> refresh(
			@Valid @RequestBody RefreshTokenRequest request
	) {
		return ResponseEntity.ok(authService.refresh(request.refreshToken()));
	}

	@PostMapping("/email/verify")
	public ResponseEntity<Void> verifyEmail(
			@Valid @RequestBody VerifyEmailRequest request
	) {
		authService.verifyEmail(request.token());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/email/resend")
	public ResponseEntity<Void> resendEmailVerification(
			@Valid @RequestBody ResendEmailVerificationRequest request
	) {
		authService.resendEmailVerification(request.email());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/password/forgot")
	public ResponseEntity<Void> forgotPassword(
			@Valid @RequestBody ForgotPasswordRequest request
	) {
		authService.requestPasswordReset(request.email());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/password/reset")
	public ResponseEntity<Void> resetPassword(
			@Valid @RequestBody ResetPasswordRequest request
	) {
		authService.resetPassword(request.token(), request.newPassword());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@Valid @RequestBody RefreshTokenRequest request,
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		authService.logout(request.refreshToken(), authenticatedUser.id());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/logout-all")
	public ResponseEntity<Void> logoutAll(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		authService.logoutAll(authenticatedUser.id());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	public ResponseEntity<AuthUserResponse> me(
			@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser
	) {
		User user = userService.findById(authenticatedUser.id());
		return ResponseEntity.ok(AuthUserResponse.from(user));
	}
}
