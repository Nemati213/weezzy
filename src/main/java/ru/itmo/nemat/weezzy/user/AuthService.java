package ru.itmo.nemat.weezzy.user;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.security.JwtService;
import ru.itmo.nemat.weezzy.security.session.AuthSessionService;
import ru.itmo.nemat.weezzy.security.session.AuthSessionService.IssuedRefreshToken;
import ru.itmo.nemat.weezzy.security.session.AuthSessionService.RotatedRefreshToken;
import ru.itmo.nemat.weezzy.security.session.InvalidRefreshTokenException;
import ru.itmo.nemat.weezzy.user.dto.AuthTokenResponse;
import ru.itmo.nemat.weezzy.user.dto.RegistrationResponse;
import ru.itmo.nemat.weezzy.user.emailverification.EmailNotVerifiedException;
import ru.itmo.nemat.weezzy.user.emailverification.EmailVerificationRequestedEvent;
import ru.itmo.nemat.weezzy.user.emailverification.EmailVerificationService;
import ru.itmo.nemat.weezzy.user.emailverification.EmailVerificationService.IssuedEmailVerificationToken;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
	private final UserService userService;
	private final JwtService jwtService;
	private final AuthSessionService authSessionService;
	private final EmailVerificationService emailVerificationService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public RegistrationResponse register(
			String email,
			String rawPassword
	) {
		User user = userService.register(email, rawPassword);
		IssuedEmailVerificationToken issuedToken = emailVerificationService.createToken(user);
		publishVerificationEvent(user, issuedToken);
		return new RegistrationResponse(user.getEmail(), true);
	}

	@Transactional
	public AuthTokenResponse login(
			String email,
			String rawPassword,
			String userAgent,
			String ipAddress
	) {
		User user = userService.authenticate(email, rawPassword);
		if (user.getEmailVerifiedAt() == null) {
			throw new EmailNotVerifiedException();
		}
		return createTokenResponse(user, authSessionService.createSession(
				user,
				userAgent,
				ipAddress
		));
	}

	@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
	public AuthTokenResponse refresh(String rawRefreshToken) {
		RotatedRefreshToken rotatedToken = authSessionService.rotate(rawRefreshToken);
		return AuthTokenResponse.bearer(
				jwtService.generateAccessToken(rotatedToken.user()),
				rotatedToken.value(),
				jwtService.getAccessTokenTtlSeconds(),
				rotatedToken.expiresAt(),
				rotatedToken.user()
		);
	}

	public void logout(String rawRefreshToken, UUID authenticatedUserId) {
		authSessionService.logout(rawRefreshToken, authenticatedUserId);
	}

	public void logoutAll(UUID authenticatedUserId) {
		authSessionService.logoutAll(authenticatedUserId);
	}

	public void verifyEmail(String rawVerificationToken) {
		emailVerificationService.verify(rawVerificationToken);
	}

	@Transactional
	public void resendEmailVerification(String email) {
		userService.findOptionalByEmail(email).ifPresent(user ->
				emailVerificationService.reissueToken(user)
						.ifPresent(issuedToken -> publishVerificationEvent(user, issuedToken))
		);
	}

	private AuthTokenResponse createTokenResponse(User user, IssuedRefreshToken refreshToken) {
		return AuthTokenResponse.bearer(
				jwtService.generateAccessToken(user),
				refreshToken.value(),
				jwtService.getAccessTokenTtlSeconds(),
				refreshToken.expiresAt(),
				user
		);
	}

	private void publishVerificationEvent(
			User user,
			IssuedEmailVerificationToken issuedToken
	) {
		eventPublisher.publishEvent(new EmailVerificationRequestedEvent(
				user.getEmail(),
				issuedToken.value(),
				issuedToken.expiresAt()
		));
	}
}
