package ru.itmo.nemat.weezzy.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.nemat.weezzy.security.JwtAuthenticatedUser;
import ru.itmo.nemat.weezzy.security.JwtService;
import ru.itmo.nemat.weezzy.user.dto.AuthTokenResponse;
import ru.itmo.nemat.weezzy.user.dto.AuthUserResponse;
import ru.itmo.nemat.weezzy.user.dto.LoginRequest;
import ru.itmo.nemat.weezzy.user.dto.RegisterRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
	private final UserService userService;
	private final JwtService jwtService;

	@PostMapping("/register")
	public ResponseEntity<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
		User user = userService.register(request.email(), request.password());

		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(AuthTokenResponse.bearer(jwtService.generateAccessToken(user), user));
	}

	@PostMapping("/login")
	public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
		User user = userService.authenticate(request.email(), request.password());

		return ResponseEntity.ok(AuthTokenResponse.bearer(jwtService.generateAccessToken(user), user));
	}

	@GetMapping("/me")
	public ResponseEntity<AuthUserResponse> me(@AuthenticationPrincipal JwtAuthenticatedUser authenticatedUser) {
		User user = userService.findById(authenticatedUser.id());

		return ResponseEntity.ok(AuthUserResponse.from(user));
	}
}
