package ru.itmo.nemat.weezzy.user.passwordreset;

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
import ru.itmo.nemat.weezzy.user.UserService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class PasswordResetServiceTests {
	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17")
					.asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private PasswordResetService passwordResetService;

	@Autowired
	private PasswordResetTokenRepository tokenRepository;

	@Autowired
	private PasswordResetTokenCodec tokenCodec;

	@Autowired
	private UserService userService;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void createTokenStoresOnlySecretHash() {
		User user = registerUser();

		PasswordResetService.IssuedPasswordResetToken issuedToken =
				passwordResetService.createToken(user);
		PasswordResetTokenCodec.ParsedPasswordResetToken parsedToken = tokenCodec
				.parse(issuedToken.value())
				.orElseThrow();
		PasswordResetToken storedToken = tokenRepository.findById(parsedToken.id())
				.orElseThrow();

		assertThat(storedToken.getSecretHash()).hasSize(64);
		assertThat(storedToken.getSecretHash()).isNotEqualTo(issuedToken.value());
		assertThat(tokenCodec.matches(
				storedToken.getSecretHash(),
				parsedToken.secretHash()
		)).isTrue();
		assertThat(storedToken.getUser().getId()).isEqualTo(user.getId());
	}

	@Test
	void resetPasswordChangesHashAndConsumesToken() {
		User user = registerUser();
		PasswordResetService.IssuedPasswordResetToken issuedToken =
				passwordResetService.createToken(user);
		UUID tokenId = tokenCodec.parse(issuedToken.value()).orElseThrow().id();

		UUID resetUserId = passwordResetService.resetPassword(
				issuedToken.value(),
				"new-password123"
		);

		PasswordResetToken usedToken = tokenRepository.findById(tokenId).orElseThrow();
		User updatedUser = userService.findById(user.getId());
		assertThat(resetUserId).isEqualTo(user.getId());
		assertThat(usedToken.getUsedAt()).isNotNull();
		assertThat(userService.passwordMatches(updatedUser, "new-password123")).isTrue();
		assertThat(userService.passwordMatches(updatedUser, "password123")).isFalse();
	}

	@Test
	void resetRejectsInvalidSecretWithoutChangingPassword() {
		User user = registerUser();
		PasswordResetService.IssuedPasswordResetToken issuedToken =
				passwordResetService.createToken(user);
		UUID tokenId = tokenCodec.parse(issuedToken.value()).orElseThrow().id();

		assertThatThrownBy(() -> passwordResetService.resetPassword(
				tokenId + ".wrong-secret",
				"new-password123"
		))
				.isInstanceOf(InvalidPasswordResetTokenException.class)
				.hasMessage("Password reset token is invalid or expired");

		assertThat(userService.passwordMatches(
				userService.findById(user.getId()),
				"password123"
		)).isTrue();
	}

	@Test
	void replacementRevokesPreviousToken() {
		User user = registerUser();
		PasswordResetService.IssuedPasswordResetToken firstToken =
				passwordResetService.createToken(user);
		UUID firstTokenId = tokenCodec.parse(firstToken.value()).orElseThrow().id();

		PasswordResetService.IssuedPasswordResetToken replacementToken =
				passwordResetService.createToken(user);

		assertThat(tokenRepository.findById(firstTokenId).orElseThrow().getRevokedAt())
				.isNotNull();
		assertThatThrownBy(() -> passwordResetService.resetPassword(
				firstToken.value(),
				"new-password123"
		)).isInstanceOf(InvalidPasswordResetTokenException.class);

		passwordResetService.resetPassword(replacementToken.value(), "new-password123");
		assertThat(userService.passwordMatches(
				userService.findById(user.getId()),
				"new-password123"
		)).isTrue();
	}

	private User registerUser() {
		return userService.register(
				"password-reset-" + UUID.randomUUID() + "@example.com",
				"password123"
		);
	}
}
