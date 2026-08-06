package ru.itmo.nemat.weezzy.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ru.itmo.nemat.weezzy.user.emailverification.LocalEmailVerificationSender;
import ru.itmo.nemat.weezzy.user.passwordreset.LocalPasswordResetSender;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

	private static final DockerImageName POSTGRES_IMAGE =
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("weezzy")
			.withUsername("weezzy")
			.withPassword("weezzy_dev_password");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void registerCreatesUnverifiedUserWithoutAuthenticationTokens() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "AuthRegister@ITMO.ru",
								  "password": "password123"
								}
				"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("authregister@itmo.ru"))
				.andExpect(jsonPath("$.verificationRequired").value(true))
				.andExpect(jsonPath("$.accessToken").doesNotExist())
				.andExpect(jsonPath("$.refreshToken").doesNotExist());
	}

	@Test
	void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
		register("duplicate-auth@itmo.ru", "password123")
				.andExpect(status().isCreated());

		register("DUPLICATE-AUTH@ITMO.RU", "password456")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value("User already exists: duplicate-auth@itmo.ru"));
	}

	@Test
	void registerRejectsInvalidEmail() throws Exception {
		register("not-an-email", "password123")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("email")));
	}

	@Test
	void registerRejectsShortPassword() throws Exception {
		register("short-password@itmo.ru", "123")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("password")));
	}

	@Test
	void loginReturnsUserForCorrectPassword() throws Exception {
		registerAndVerify("login-user@itmo.ru", "password123");

		login("LOGIN-USER@ITMO.RU", "password123")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.user.id").isNotEmpty())
				.andExpect(jsonPath("$.user.email").value("login-user@itmo.ru"))
				.andExpect(jsonPath("$.user.role").value("USER"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void loginRejectsUserUntilEmailIsVerified() throws Exception {
		register("unverified-login@itmo.ru", "password123")
				.andExpect(status().isCreated());

		login("unverified-login@itmo.ru", "password123")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.message").value("Email is not verified"));
	}

	@Test
	void verifyEmailAllowsSubsequentLogin() throws Exception {
		String email = "verify-controller@itmo.ru";
		register(email, "password123").andExpect(status().isCreated());
		String verificationToken = takeVerificationToken(email);

		verifyEmail(verificationToken)
				.andExpect(status().isNoContent());

		login(email, "password123")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty());
	}

	@Test
	void verifyEmailRejectsInvalidToken() throws Exception {
		verifyEmail("00000000-0000-0000-0000-000000000000.invalid")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message")
						.value("Email verification token is invalid or expired"));
	}

	@Test
	void resendReplacesActiveTokenAndAlwaysReturnsAccepted() throws Exception {
		String email = "resend-controller@itmo.ru";
		register(email, "password123").andExpect(status().isCreated());
		String originalToken = takeVerificationToken(email);

		resendEmailVerification(email)
				.andExpect(status().isAccepted());
		String replacementToken = takeVerificationToken(email);
		assertThat(replacementToken).isNotEqualTo(originalToken);

		verifyEmail(originalToken).andExpect(status().isUnauthorized());
		verifyEmail(replacementToken).andExpect(status().isNoContent());
		resendEmailVerification(email).andExpect(status().isAccepted());
		assertThat(LocalEmailVerificationSender.takeToken(email)).isEmpty();
		resendEmailVerification("missing-resend@itmo.ru")
				.andExpect(status().isAccepted());
	}

	@Test
	void forgotPasswordAlwaysReturnsAcceptedAndSendsOnlyForExistingUser()
			throws Exception {
		String email = "forgot-password@itmo.ru";
		registerAndVerify(email, "password123");

		forgotPassword(email)
				.andExpect(status().isAccepted());
		assertThat(takePasswordResetToken(email)).isNotBlank();

		String missingEmail = "missing-forgot-password@itmo.ru";
		forgotPassword(missingEmail)
				.andExpect(status().isAccepted());
		assertThat(LocalPasswordResetSender.takeToken(missingEmail)).isEmpty();
	}

	@Test
	void resetPasswordChangesCredentialsAndConsumesToken() throws Exception {
		String email = "reset-password@itmo.ru";
		registerAndVerify(email, "password123");
		forgotPassword(email).andExpect(status().isAccepted());
		String resetToken = takePasswordResetToken(email);

		resetPassword(resetToken, "new-password123")
				.andExpect(status().isNoContent());

		login(email, "password123")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
		login(email, "new-password123")
				.andExpect(status().isOk());
		resetPassword(resetToken, "another-password123")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message")
						.value("Password reset token is invalid or expired"));
	}

	@Test
	void newPasswordResetRequestInvalidatesPreviousToken() throws Exception {
		String email = "replace-reset-token@itmo.ru";
		registerAndVerify(email, "password123");
		forgotPassword(email).andExpect(status().isAccepted());
		String firstToken = takePasswordResetToken(email);
		forgotPassword(email).andExpect(status().isAccepted());
		String replacementToken = takePasswordResetToken(email);

		assertThat(replacementToken).isNotEqualTo(firstToken);
		resetPassword(firstToken, "new-password123")
				.andExpect(status().isUnauthorized());
		resetPassword(replacementToken, "new-password123")
				.andExpect(status().isNoContent());
	}

	@Test
	void resetPasswordRevokesAllRefreshSessions() throws Exception {
		String email = "reset-revokes-sessions@itmo.ru";
		JsonNode firstSession = registerAndRead(email, "password123");
		JsonNode secondSession = loginAndRead(email, "password123");
		forgotPassword(email).andExpect(status().isAccepted());

		resetPassword(takePasswordResetToken(email), "new-password123")
				.andExpect(status().isNoContent());

		refresh(firstSession.path("refreshToken").asText())
				.andExpect(status().isUnauthorized());
		refresh(secondSession.path("refreshToken").asText())
				.andExpect(status().isUnauthorized());
		login(email, "new-password123")
				.andExpect(status().isOk());
	}

	@Test
	void resetPasswordDoesNotVerifyEmail() throws Exception {
		String email = "unverified-password-reset@itmo.ru";
		register(email, "password123").andExpect(status().isCreated());
		forgotPassword(email).andExpect(status().isAccepted());

		resetPassword(takePasswordResetToken(email), "new-password123")
				.andExpect(status().isNoContent());

		login(email, "new-password123")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Email is not verified"));
	}

	@Test
	void refreshRotatesTokenAndReturnsNewTokenPair() throws Exception {
		JsonNode registered = registerAndRead("refresh-user@itmo.ru", "password123");
		String originalRefreshToken = registered.path("refreshToken").asText();

		String response = refresh(originalRefreshToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.expiresIn").value(900))
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode refreshed = objectMapper.readTree(response);

		assertThat(refreshed.path("refreshToken").asText())
				.isNotEqualTo(originalRefreshToken);
	}

	@Test
	void reusedRefreshTokenRevokesEntireSession() throws Exception {
		JsonNode registered = registerAndRead("reuse-user@itmo.ru", "password123");
		String originalRefreshToken = registered.path("refreshToken").asText();
		String rotatedResponse = refresh(originalRefreshToken)
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		String rotatedRefreshToken = objectMapper.readTree(rotatedResponse)
				.path("refreshToken")
				.asText();

		refresh(originalRefreshToken)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message")
						.value("Refresh token is invalid or expired"));

		refresh(rotatedRefreshToken)
				.andExpect(status().isUnauthorized());
	}

	@Test
	void invalidRefreshSecretDoesNotRevokeSession() throws Exception {
		JsonNode registered = registerAndRead("invalid-secret@itmo.ru", "password123");
		String refreshToken = registered.path("refreshToken").asText();
		String tokenId = refreshToken.substring(0, refreshToken.indexOf('.'));

		refresh(tokenId + ".invalid-secret")
				.andExpect(status().isUnauthorized());

		refresh(refreshToken)
				.andExpect(status().isOk());
	}

	@Test
	void logoutRevokesOnlyCurrentSession() throws Exception {
		JsonNode firstSession = registerAndRead("logout-user@itmo.ru", "password123");
		JsonNode secondSession = loginAndRead("logout-user@itmo.ru", "password123");

		mockMvc.perform(post("/api/auth/logout")
						.header(HttpHeaders.AUTHORIZATION,
								"Bearer " + firstSession.path("accessToken").asText())
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshBody(firstSession.path("refreshToken").asText())))
				.andExpect(status().isNoContent());

		refresh(firstSession.path("refreshToken").asText())
				.andExpect(status().isUnauthorized());
		refresh(secondSession.path("refreshToken").asText())
				.andExpect(status().isOk());
	}

	@Test
	void logoutAllRevokesEveryUserSession() throws Exception {
		JsonNode firstSession = registerAndRead("logout-all-user@itmo.ru", "password123");
		JsonNode secondSession = loginAndRead("logout-all-user@itmo.ru", "password123");

		mockMvc.perform(post("/api/auth/logout-all")
						.header(HttpHeaders.AUTHORIZATION,
								"Bearer " + secondSession.path("accessToken").asText()))
				.andExpect(status().isNoContent());

		refresh(firstSession.path("refreshToken").asText())
				.andExpect(status().isUnauthorized());
		refresh(secondSession.path("refreshToken").asText())
				.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshDoesNotRequireAccessToken() throws Exception {
		JsonNode registered = registerAndRead("public-refresh@itmo.ru", "password123");

		refresh(registered.path("refreshToken").asText())
				.andExpect(status().isOk());
	}

	@Test
	void loginRejectsWrongPassword() throws Exception {
		register("wrong-login@itmo.ru", "password123")
				.andExpect(status().isCreated());

		login("wrong-login@itmo.ru", "password456")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void loginRejectsMissingUserAsInvalidCredentials() throws Exception {
		login("missing-user@itmo.ru", "password123")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void meReturnsCurrentUserForValidToken() throws Exception {
		String token = registerAndGetToken("me-user@itmo.ru", "password123");

		mockMvc.perform(get("/api/auth/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.email").value("me-user@itmo.ru"))
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void meRejectsMissingToken() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().exists("X-Request-ID"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Authentication is required"))
				.andExpect(jsonPath("$.path").value("/api/auth/me"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void authorizationFailureUsesSameErrorFormat() throws Exception {
		String token = registerAndGetToken("forbidden-user@itmo.ru", "password123");

		mockMvc.perform(post("/api/goals")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Forbidden goal",
								  "description": "Regular users cannot create goals"
								}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(header().exists("X-Request-ID"))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.error").value("Forbidden"))
				.andExpect(jsonPath("$.message").value("Access is denied"))
				.andExpect(jsonPath("$.path").value("/api/goals"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void requestIdIsPropagatedThroughApiErrors() throws Exception {
		String requestId = "mobile-request-42";

		mockMvc.perform(post("/api/auth/register")
						.header("X-Request-ID", requestId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "invalid-email",
								  "password": "password123"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(header().string("X-Request-ID", requestId))
				.andExpect(jsonPath("$.requestId").value(requestId));
	}

	@Test
	void actuatorHealthProbesArePublic() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));

		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void actuatorMetricsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/actuator/metrics"))
				.andExpect(status().isUnauthorized());

		String token = registerAndGetToken("metrics-user@itmo.ru", "password123");
		mockMvc.perform(get("/actuator/metrics")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.names", hasItem("http.server.requests")));

		mockMvc.perform(get("/actuator/prometheus")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void openApiDocumentationIsPublicAndContainsBearerAuthentication() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Weezzy API"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type")
						.value("http"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
						.value("bearer"))
				.andExpect(jsonPath("$.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/auth/login'].post.security")
						.doesNotExist())
				.andExpect(jsonPath("$.paths['/api/auth/refresh'].post.security")
						.doesNotExist())
				.andExpect(jsonPath("$.paths['/api/auth/email/verify'].post.security")
						.doesNotExist())
				.andExpect(jsonPath("$.paths['/api/auth/email/resend'].post.security")
						.doesNotExist())
				.andExpect(jsonPath("$.paths['/api/auth/password/forgot'].post.security")
						.doesNotExist())
				.andExpect(jsonPath("$.paths['/api/auth/password/reset'].post.security")
						.doesNotExist())
				.andExpect(jsonPath(
						"$.paths['/api/recommendations'].get.security[0].bearerAuth"
				).isArray())
				.andExpect(jsonPath(
						"$.paths['/api/users/me'].delete.security[0].bearerAuth"
				).isArray())
				.andExpect(jsonPath("$.paths['/api/recommendations']").exists());
	}

	@Test
	void swaggerUiIsPublic() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection());
	}

	private ResultActions register(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "%s"
						}
						""".formatted(email, password)));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "%s"
						}
						""".formatted(email, password)));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(refreshToken)));
	}

	private ResultActions verifyEmail(String verificationToken) throws Exception {
		return mockMvc.perform(post("/api/auth/email/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
						java.util.Map.of("token", verificationToken)
				)));
	}

	private ResultActions resendEmailVerification(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/email/resend")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("email", email))));
	}

	private ResultActions forgotPassword(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/password/forgot")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("email", email))));
	}

	private ResultActions resetPassword(
			String resetToken,
			String newPassword
	) throws Exception {
		return mockMvc.perform(post("/api/auth/password/reset")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
						"token", resetToken,
						"newPassword", newPassword
				))));
	}

	private JsonNode registerAndRead(String email, String password) throws Exception {
		registerAndVerify(email, password);
		String response = login(email, password)
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response);
	}

	private void registerAndVerify(String email, String password) throws Exception {
		register(email, password).andExpect(status().isCreated());
		verifyEmail(takeVerificationToken(email)).andExpect(status().isNoContent());
	}

	private String takeVerificationToken(String email) {
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		return LocalEmailVerificationSender.takeToken(normalizedEmail)
				.orElseThrow(() -> new IllegalStateException(
						"No local verification token was sent to " + normalizedEmail
				));
	}

	private String takePasswordResetToken(String email) {
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		return LocalPasswordResetSender.takeToken(normalizedEmail)
				.orElseThrow(() -> new IllegalStateException(
						"No local password reset token was sent to " + normalizedEmail
				));
	}

	private JsonNode loginAndRead(String email, String password) throws Exception {
		String response = login(email, password)
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response);
	}

	private String refreshBody(String refreshToken) throws Exception {
		return objectMapper.writeValueAsString(
				java.util.Map.of("refreshToken", refreshToken)
		);
	}

	private String registerAndGetToken(String email, String password) throws Exception {
		return registerAndRead(email, password).path("accessToken").asText();
	}
}
