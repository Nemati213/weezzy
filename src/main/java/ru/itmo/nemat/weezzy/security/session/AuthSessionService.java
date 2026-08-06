package ru.itmo.nemat.weezzy.security.session;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.security.session.RefreshTokenCodec.GeneratedRefreshToken;
import ru.itmo.nemat.weezzy.security.session.RefreshTokenCodec.ParsedRefreshToken;
import ru.itmo.nemat.weezzy.user.User;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthSessionService {
	private static final int MAX_USER_AGENT_LENGTH = 512;
	private static final int MAX_IP_ADDRESS_LENGTH = 45;

	private final AuthSessionRepository authSessionRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final RefreshTokenCodec refreshTokenCodec;

	@Value("${app.security.session.refresh-token-ttl:P30D}")
	private Duration refreshTokenTtl;

	@Value("${app.security.session.max-ttl:P90D}")
	private Duration sessionMaxTtl;

	@Transactional
	public IssuedRefreshToken createSession(User user, String userAgent, String ipAddress) {
		LocalDateTime now = LocalDateTime.now();
		AuthSession session = new AuthSession();
		session.setUser(user);
		session.setCreatedAt(now);
		session.setLastUsedAt(now);
		session.setExpiresAt(now.plus(sessionMaxTtl));
		session.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
		session.setIpAddress(truncate(ipAddress, MAX_IP_ADDRESS_LENGTH));
		authSessionRepository.save(session);

		return createRefreshToken(session, now);
	}

	@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
	public RotatedRefreshToken rotate(String rawRefreshToken) {
		ParsedRefreshToken parsedToken = refreshTokenCodec.parse(rawRefreshToken)
				.orElseThrow(InvalidRefreshTokenException::new);
		RefreshToken currentToken = refreshTokenRepository.findByIdForUpdate(parsedToken.id())
				.orElseThrow(InvalidRefreshTokenException::new);

		if (!refreshTokenCodec.matches(currentToken.getSecretHash(), parsedToken.secretHash())) {
			throw new InvalidRefreshTokenException();
		}

		AuthSession session = currentToken.getSession();
		LocalDateTime now = LocalDateTime.now();
		if (session.getRevokedAt() != null || currentToken.getRevokedAt() != null) {
			throw new InvalidRefreshTokenException();
		}
		if (currentToken.getUsedAt() != null) {
			revoke(session, AuthSessionRevokeReason.TOKEN_REUSE, now);
			throw new InvalidRefreshTokenException();
		}
		if (!currentToken.getExpiresAt().isAfter(now) || !session.getExpiresAt().isAfter(now)) {
			revoke(session, AuthSessionRevokeReason.EXPIRED, now);
			throw new InvalidRefreshTokenException();
		}

		GeneratedRefreshToken generatedToken = refreshTokenCodec.generate();
		currentToken.setUsedAt(now);
		refreshTokenRepository.flush();

		RefreshToken nextToken = buildRefreshToken(session, generatedToken, now);
		refreshTokenRepository.saveAndFlush(nextToken);
		currentToken.setReplacedByTokenId(generatedToken.id());
		session.setLastUsedAt(now);

		return new RotatedRefreshToken(
				session.getUser(),
				generatedToken.value(),
				nextToken.getExpiresAt()
		);
	}

	@Transactional
	public void logout(String rawRefreshToken, UUID authenticatedUserId) {
		Optional<ParsedRefreshToken> parsedToken = refreshTokenCodec.parse(rawRefreshToken);
		if (parsedToken.isEmpty()) {
			return;
		}

		refreshTokenRepository.findByIdForUpdate(parsedToken.get().id())
				.filter(token -> refreshTokenCodec.matches(
						token.getSecretHash(),
						parsedToken.get().secretHash()
				))
				.filter(token -> token.getSession().getUser().getId().equals(authenticatedUserId))
				.map(RefreshToken::getSession)
				.filter(session -> session.getRevokedAt() == null)
				.ifPresent(session -> revoke(
						session,
						AuthSessionRevokeReason.LOGOUT,
						LocalDateTime.now()
				));
	}

	@Transactional
	public void logoutAll(UUID userId) {
		revokeAll(userId, AuthSessionRevokeReason.LOGOUT_ALL);
	}

	@Transactional
	public void revokeAllAfterPasswordReset(UUID userId) {
		revokeAll(userId, AuthSessionRevokeReason.PASSWORD_RESET);
	}

	private void revokeAll(UUID userId, AuthSessionRevokeReason reason) {
		authSessionRepository.revokeAllByUserId(
				userId,
				LocalDateTime.now(),
				reason.name()
		);
	}

	private IssuedRefreshToken createRefreshToken(AuthSession session, LocalDateTime now) {
		GeneratedRefreshToken generatedToken = refreshTokenCodec.generate();
		RefreshToken refreshToken = buildRefreshToken(session, generatedToken, now);
		refreshTokenRepository.save(refreshToken);

		return new IssuedRefreshToken(generatedToken.value(), refreshToken.getExpiresAt());
	}

	private RefreshToken buildRefreshToken(
			AuthSession session,
			GeneratedRefreshToken generatedToken,
			LocalDateTime now
	) {
		RefreshToken refreshToken = new RefreshToken();
		refreshToken.setId(generatedToken.id());
		refreshToken.setSession(session);
		refreshToken.setSecretHash(generatedToken.secretHash());
		refreshToken.setCreatedAt(now);
		refreshToken.setExpiresAt(min(now.plus(refreshTokenTtl), session.getExpiresAt()));
		return refreshToken;
	}

	private void revoke(
			AuthSession session,
			AuthSessionRevokeReason reason,
			LocalDateTime revokedAt
	) {
		session.setRevokedAt(revokedAt);
		session.setRevokeReason(reason.name());
	}

	private LocalDateTime min(LocalDateTime first, LocalDateTime second) {
		return first.isBefore(second) ? first : second;
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	public record IssuedRefreshToken(String value, LocalDateTime expiresAt) {
	}

	public record RotatedRefreshToken(
			User user,
			String value,
			LocalDateTime expiresAt
	) {
	}
}
