package ru.itmo.nemat.weezzy.user.emailverification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.itmo.nemat.weezzy.user.User;
import ru.itmo.nemat.weezzy.user.UserRepository;
import ru.itmo.nemat.weezzy.user.UserService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@Testcontainers
@SpringBootTest
class EmailVerificationServiceTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private EmailVerificationService emailVerificationService;

	@Autowired
	private EmailVerificationTokenRepository tokenRepository;

	@Autowired
	private EmailVerificationTokenCodec tokenCodec;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void createTokenStoresOnlySecretHash() {
		User user = registerUser();

		EmailVerificationService.IssuedEmailVerificationToken issuedToken =
				emailVerificationService.createToken(user);
		EmailVerificationTokenCodec.ParsedEmailVerificationToken parsedToken = tokenCodec
				.parse(issuedToken.value())
				.orElseThrow();
		EmailVerificationToken storedToken = tokenRepository.findById(parsedToken.id())
				.orElseThrow();

		assertThat(storedToken.getSecretHash()).hasSize(64);
		assertThat(storedToken.getSecretHash()).isNotEqualTo(issuedToken.value());
		assertThat(tokenCodec.matches(
				storedToken.getSecretHash(),
				parsedToken.secretHash()
		)).isTrue();
		assertThat(storedToken.getUser().getId()).isEqualTo(user.getId());
		assertThat(storedToken.getExpiresAt())
				.isCloseTo(issuedToken.expiresAt(), within(1, ChronoUnit.MICROS));
	}

	@Test
	void verifyMarksTokenUsedAndUserEmailVerified() {
		User user = registerUser();
		EmailVerificationService.IssuedEmailVerificationToken issuedToken =
				emailVerificationService.createToken(user);
		UUID tokenId = tokenCodec.parse(issuedToken.value()).orElseThrow().id();

		emailVerificationService.verify(issuedToken.value());

		User verifiedUser = userRepository.findById(user.getId()).orElseThrow();
		EmailVerificationToken usedToken = tokenRepository.findById(tokenId).orElseThrow();
		assertThat(verifiedUser.getEmailVerifiedAt()).isNotNull();
		assertThat(usedToken.getUsedAt()).isNotNull();
		assertThat(usedToken.getRevokedAt()).isNull();
	}

	@Test
	void verifyRejectsInvalidSecretWithoutVerifyingUser() {
		User user = registerUser();
		EmailVerificationService.IssuedEmailVerificationToken issuedToken =
				emailVerificationService.createToken(user);
		UUID tokenId = tokenCodec.parse(issuedToken.value()).orElseThrow().id();

		assertThatThrownBy(() -> emailVerificationService.verify(tokenId + ".wrong-secret"))
				.isInstanceOf(InvalidEmailVerificationTokenException.class)
				.hasMessage("Email verification token is invalid or expired");

		User unverifiedUser = userRepository.findById(user.getId()).orElseThrow();
		assertThat(unverifiedUser.getEmailVerifiedAt()).isNull();
	}

	@Test
	void reissueRevokesPreviousTokenAndCreatesReplacement() {
		User user = registerUser();
		EmailVerificationService.IssuedEmailVerificationToken firstToken =
				emailVerificationService.createToken(user);
		UUID firstTokenId = tokenCodec.parse(firstToken.value()).orElseThrow().id();

		EmailVerificationService.IssuedEmailVerificationToken replacementToken =
				emailVerificationService.reissueToken(user).orElseThrow();

		EmailVerificationToken revokedToken = tokenRepository.findById(firstTokenId)
				.orElseThrow();
		assertThat(revokedToken.getRevokedAt()).isNotNull();
		assertThat(replacementToken.value()).isNotEqualTo(firstToken.value());
		assertThatThrownBy(() -> emailVerificationService.verify(firstToken.value()))
				.isInstanceOf(InvalidEmailVerificationTokenException.class);

		emailVerificationService.verify(replacementToken.value());
		assertThat(userRepository.findById(user.getId()).orElseThrow().getEmailVerifiedAt())
				.isNotNull();
	}

	@Test
	void reissueDoesNothingForAlreadyVerifiedUser() {
		User user = registerUser();
		user.setEmailVerifiedAt(LocalDateTime.now());
		userRepository.save(user);

		assertThat(emailVerificationService.reissueToken(user)).isEmpty();
		assertThat(tokenRepository.findAll())
				.noneMatch(token -> token.getUser().getId().equals(user.getId()));
	}

	@Test
	void createTokenRejectsAlreadyVerifiedUserAsInvalidInternalCall() {
		User user = registerUser();
		user.setEmailVerifiedAt(LocalDateTime.now());
		userRepository.save(user);

		assertThatThrownBy(() -> emailVerificationService.createToken(user))
				.isInstanceOf(EmailAlreadyVerifiedException.class)
				.hasMessage("Email is already verified");
	}

	private User registerUser() {
		return userService.register(
				"verification-" + UUID.randomUUID() + "@example.com",
				"password123"
		);
	}
}
