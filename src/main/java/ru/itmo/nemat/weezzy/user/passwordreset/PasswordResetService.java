package ru.itmo.nemat.weezzy.user.passwordreset;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
	private final PasswordResetTokenRepository repository;
	private final PasswordResetTokenCodec codec;
	private final UserService userService;

	@Value("${app.security.password-reset.token-ttl:PT30M}")
	private Duration passwordResetTokenTtl;

	@Transactional
	public IssuedPasswordResetToken createToken(User user) {
		LocalDateTime now = LocalDateTime.now();
		repository.findActiveByUserIdForUpdate(user.getId()).ifPresent(currentToken -> {
			currentToken.setRevokedAt(now);
			repository.saveAndFlush(currentToken);
		});

		PasswordResetTokenCodec.GeneratedPasswordResetToken generatedToken =
				codec.generate();
		PasswordResetToken token = new PasswordResetToken();
		token.setId(generatedToken.id());
		token.setUser(user);
		token.setSecretHash(generatedToken.secretHash());
		token.setCreatedAt(now);
		token.setExpiresAt(now.plus(passwordResetTokenTtl));
		repository.save(token);

		return new IssuedPasswordResetToken(
				generatedToken.value(),
				token.getExpiresAt()
		);
	}

	@Transactional
	public UUID resetPassword(String rawToken, String newRawPassword) {
		PasswordResetTokenCodec.ParsedPasswordResetToken parsedToken = codec.parse(rawToken)
				.orElseThrow(InvalidPasswordResetTokenException::new);
		PasswordResetToken currentToken = repository.findByIdForUpdate(parsedToken.id())
				.orElseThrow(InvalidPasswordResetTokenException::new);

		if (!codec.matches(currentToken.getSecretHash(), parsedToken.secretHash())) {
			throw new InvalidPasswordResetTokenException();
		}

		LocalDateTime now = LocalDateTime.now();
		if (currentToken.getUsedAt() != null
				|| currentToken.getRevokedAt() != null
				|| !currentToken.getExpiresAt().isAfter(now)) {
			throw new InvalidPasswordResetTokenException();
		}

		userService.updatePassword(currentToken.getUser(), newRawPassword);
		currentToken.setUsedAt(now);
		repository.save(currentToken);
		return currentToken.getUser().getId();
	}

	public record IssuedPasswordResetToken(
			String value,
			LocalDateTime expiresAt
	) {
	}
}
