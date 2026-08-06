package ru.itmo.nemat.weezzy.user.emailverification;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.emailverification.EmailVerificationTokenCodec.GeneratedEmailVerificationToken;
import ru.itmo.nemat.weezzy.user.emailverification.EmailVerificationTokenCodec.ParsedEmailVerificationToken;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
	private final EmailVerificationTokenRepository repository;
	private final EmailVerificationTokenCodec codec;

	@Value("${app.security.email-verification.token-ttl:PT24H}")
	private Duration emailVerificationTokenTtl;

	@Transactional
	public IssuedEmailVerificationToken createToken(User user) {
		ensureEmailIsNotVerified(user);
		return replaceActiveToken(user, LocalDateTime.now());
	}

	@Transactional
	public void verify(String rawEmailVerificationToken) {
		ParsedEmailVerificationToken parsedToken = codec.parse(rawEmailVerificationToken)
				.orElseThrow(InvalidEmailVerificationTokenException::new);
		EmailVerificationToken currentToken = repository.findByIdForUpdate(parsedToken.id())
				.orElseThrow(InvalidEmailVerificationTokenException::new);

		if (!codec.matches(currentToken.getSecretHash(), parsedToken.secretHash())) {
			throw new InvalidEmailVerificationTokenException();
		}

		LocalDateTime now = LocalDateTime.now();
		if (currentToken.getUsedAt() != null
				|| currentToken.getRevokedAt() != null
				|| !currentToken.getExpiresAt().isAfter(now)) {
			throw new InvalidEmailVerificationTokenException();
		}

		currentToken.getUser().setEmailVerifiedAt(now);
		currentToken.setUsedAt(now);
		repository.save(currentToken);
	}

	@Transactional
	public Optional<IssuedEmailVerificationToken> reissueToken(User user) {
		if (user.getEmailVerifiedAt() != null) {
			return Optional.empty();
		}
		return Optional.of(replaceActiveToken(user, LocalDateTime.now()));
	}

	private IssuedEmailVerificationToken replaceActiveToken(
			User user,
			LocalDateTime now
	) {
		repository.findActiveByUserIdForUpdate(user.getId()).ifPresent(currentToken -> {
			currentToken.setRevokedAt(now);
			repository.saveAndFlush(currentToken);
		});

		GeneratedEmailVerificationToken generatedToken = codec.generate();
		EmailVerificationToken token = new EmailVerificationToken();
		token.setId(generatedToken.id());
		token.setUser(user);
		token.setSecretHash(generatedToken.secretHash());
		token.setCreatedAt(now);
		token.setExpiresAt(now.plus(emailVerificationTokenTtl));
		repository.save(token);

		return new IssuedEmailVerificationToken(
				generatedToken.value(),
				token.getExpiresAt()
		);
	}

	private void ensureEmailIsNotVerified(User user) {
		if (user.getEmailVerifiedAt() != null) {
			throw new EmailAlreadyVerifiedException();
		}
	}

	public record IssuedEmailVerificationToken(
			String value,
			LocalDateTime expiresAt
	) {
	}
}
