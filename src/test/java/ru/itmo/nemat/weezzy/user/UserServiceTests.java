package ru.itmo.nemat.weezzy.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class UserServiceTests {

	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

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
	void registerCreatesUserWithEncodedPassword() {
		User user = userService.register("  NematulloPK@Outlook.COM  ", "password123");

		assertThat(user.getId()).isNotNull();
		assertThat(user.getEmail()).isEqualTo("nematullopk@outlook.com");
		assertThat(user.getPasswordHash()).isNotEqualTo("password123");
		assertThat(userService.passwordMatches(user, "password123")).isTrue();
		assertThat(user.getRole()).isEqualTo(UserRole.USER);
		assertThat(user.getCreatedAt()).isNotNull();
	}

	@Test
	void registerRejectsDuplicateEmailIgnoringCase() {
		userService.register("duplicate-user@itmo.ru", "password123");

		assertThatThrownBy(() -> userService.register("DUPLICATE-USER@ITMO.RU", "password456"))
				.isInstanceOf(DuplicateUserException.class)
				.hasMessage("User already exists: duplicate-user@itmo.ru");
	}

	@Test
	void findByEmailReturnsUserIgnoringCase() {
		User createdUser = userService.register("find-user@itmo.ru", "password123");

		User foundUser = userService.findByEmail("FIND-USER@ITMO.RU");

		assertThat(foundUser.getId()).isEqualTo(createdUser.getId());
	}

	@Test
	void authenticateReturnsUserForCorrectPassword() {
		User createdUser = userService.register("auth-user@itmo.ru", "password123");

		User authenticatedUser = userService.authenticate("AUTH-USER@ITMO.RU", "password123");

		assertThat(authenticatedUser.getId()).isEqualTo(createdUser.getId());
	}

	@Test
	void authenticateRejectsWrongPassword() {
		userService.register("wrong-password@itmo.ru", "password123");

		assertThatThrownBy(() -> userService.authenticate("wrong-password@itmo.ru", "password456"))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessage("Invalid email or password");
	}

	@Test
	void findByIdReturnsNotFoundForMissingUser() {
		assertThatThrownBy(() -> userService.findById(UUID.fromString("00000000-0000-0000-0000-000000000000")))
				.isInstanceOf(UserNotFoundException.class)
				.hasMessage("User not found: 00000000-0000-0000-0000-000000000000");
	}

	@Test
	void repositoryStoresPasswordHashOnly() {
		User user = userService.register("stored-hash@itmo.ru", "password123");

		User storedUser = userRepository.findById(user.getId()).orElseThrow();

		assertThat(storedUser.getPasswordHash()).startsWith("$2");
		assertThat(storedUser.getPasswordHash()).doesNotContain("password123");
	}
}
